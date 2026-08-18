package com.bulgekeyboard

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Typeface
import android.hardware.*
import android.view.*
import android.widget.*
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.*

// Mirroring React `KeyboardItem`
data class KeyData(
    val char: String, // Label
    val typedChar: String, // Actual character to commit
    val secondary: String? = null,
    val isSwitch: Boolean = false,
    val emojiResId: Int? = null,
    val isCategory: Boolean = false 
)

enum class KeyboardMode {
    ALPHA, EMOJIS, SYMBOLS
}

enum class TextCase {
    LOWER, SENTENCE, UPPER
}

class BulgeKeyboardView(context: Context) : FrameLayout(context), SensorEventListener {

    private val keys = mutableListOf<KeyView>()
    
    var onKeyPressed: ((String?) -> Unit)? = null

    private var scrollPosition = 0f
    private var targetScroll = 0f
    private var scrollVelocity = 0f

    private lateinit var sensorManager: SensorManager
    private var rotationSensor: Sensor? = null

    private val density = context.resources.displayMetrics.density

    // Interaction states
    private var currentLongPressingIndex: Int? = null
    private var lastTypedKeyChar: String? = null
    
    private var currentMode: KeyboardMode = KeyboardMode.ALPHA
    private var currentTextCase: TextCase = TextCase.LOWER
    private var revolverMode: Boolean = false
    private var useSystemEmojiPref: Boolean = false

    private val emojiCategories = listOf("Faces", "Hands", "Animals", "Food", "Nature", "Objects", "Hearts")
    private var currentEmojiCategoryIndex = 0
    private var currentEmojiCategory: String = emojiCategories[0]

    private var currentKeyDataList: List<KeyData> = emptyList()

    // --- EMOJI MAPPING ---
    private val emojiMap = mapOf(
        "grinning_face" to "😀", "grinning_face_with_big_eyes" to "😃", "grinning_face_with_smiling_eyes" to "😄", 
        "beaming_face_with_smiling_eyes" to "😁", "grinning_squinting_face" to "😆", "grinning_face_with_sweat" to "😅", 
        "rolling_on_the_floor_laughing" to "🤣", "face_with_tears_of_joy" to "😂", "slightly_smiling_face" to "🙂", 
        "upside_down_face" to "🙃", "winking_face" to "😉", "smiling_face_with_smiling_eyes" to "😊", 
        "smiling_face_with_halo" to "😇", "smiling_face_with_hearts" to "🥰", "smiling_face_with_heart_eyes" to "😍", 
        "star_struck" to "🤩", "face_blowing_a_kiss" to "😘", "kissing_face" to "😗", "smiling_face" to "☺️", 
        "kissing_face_with_closed_eyes" to "😚", "kissing_face_with_smiling_eyes" to "😙", "smiling_face_with_tear" to "🥲", 
        "face_savoring_food" to "😋", "face_with_tongue" to "😛", "winking_face_with_tongue" to "😜", 
        "squinting_face_with_tongue" to "😝", "money_mouth_face" to "🤑", "hugging_face" to "🤗", 
        "face_with_hand_over_mouth" to "🤭", "face_with_open_eyes_and_hand_over_mouth" to "🫢", 
        "face_with_peeking_eye" to "🫣", "shushing_face" to "🤫", "thinking_face" to "🤔", 
        "face_with_raised_eyebrow" to "🤨", "neutral_face" to "😐", "expressionless_face" to "😑", 
        "face_without_mouth" to "😶", "dotted_line_face" to "🫥", "face_in_clouds" to "😶‍🌫️", 
        "smirking_face" to "😏", "unamused_face" to "😒", "face_with_rolling_eyes" to "🙄", 
        "grimacing_face" to "😬", "face_exhaling" to "😮‍💨", "lying_face" to "🤥", "relieved_face" to "😌", 
        "pensive_face" to "😔", "sleepy_face" to "😪", "drooling_face" to "🤤", "sleeping_face" to "😴", 
        "face_with_medical_mask" to "😷", "face_with_thermometer" to "🤒", "face_with_head_bandage" to "🤕", 
        "nauseated_face" to "🤢", "face_vomiting" to "🤮", "sneezing_face" to "🤧", "hot_face" to "🥵", 
        "cold_face" to "🥶", "woozy_face" to "🥴", "face_with_spiral_eyes" to "😵‍💫", "exploding_head" to "🤯", 
        "cowboy_hat_face" to "🤠", "partying_face" to "🥳", "disguised_face" to "🥸", 
        "smiling_face_with_sunglasses" to "😎", "nerd_face" to "🤓", "face_with_monocle" to "🧐", 
        "confused_face" to "😕", "face_with_diagonal_mouth" to "🫤", "worried_face" to "😟", 
        "slightly_frowning_face" to "🙁", "frowning_face" to "☹️", "face_with_open_mouth" to "😮", 
        "hushed_face" to "😯", "astonished_face" to "😲", "astonished_face_drawable" to "abacus", "flushed_face" to "😳",
        "face_holding_back_tears" to "🥹", "pleading_face" to "🥺", "frowning_face_with_open_mouth" to "😦", 
        "anguished_face" to "😧", "fearful_face" to "😨", "anxious_face_with_sweat" to "😰", 
        "sad_but_relieved_face" to "😥", "loudly_crying_face" to "😭", "face_screaming_in_fear" to "😱", 
        "confounded_face" to "😖", "persevering_face" to "😣", "tired_face" to "😫", "weary_face" to "😩", 
        "yawning_face" to "🥱", "face_with_steam_from_nose" to "😤", "pouting_face" to "😡", 
        "angry_face" to "😠", "face_with_symbols_on_mouth" to "🤬", "smiling_face_with_horns" to "😈", 
        "angry_face_with_horns" to "👿", "skull" to "💀", "skull_and_crossbones" to "☠️", "pile_of_poo" to "💩", 
        "clown_face" to "🤡", "ogre" to "👹", "goblin" to "👺", "ghost" to "👻", "alien" to "👽", 
        "alien_monster" to "👾", "robot" to "🤖", "zombie" to "🧟", "man_zombie" to "🧟‍♂️", 
        "woman_zombie" to "🧟‍♀️", "vampire" to "🧛", "man_vampire" to "🧛‍♂️", "woman_vampire" to "🧛‍♀️", 
        "santa_claus" to "🎅", "mrs_claus" to "🤶", "mx_claus" to "🧑‍🎄", "baby" to "👶", 
        "old_woman" to "👵", "man_teacher" to "👨‍🏫", "man_technologist" to "👨‍💻", "woman_technologist" to "👩‍💻", 
        "man_health_worker" to "👨‍⚕️", "woman_health_worker" to "👩‍⚕️", "man_police_officer" to "👨‍✈️", 
        "woman_police_officer" to "👩‍✈️", "man_shrugging" to "🤷‍♂️", "woman_shrugging" to "🤷‍♀️", 
        "man_facepalming" to "🤦‍♂️", "woman_facepalming" to "🤦‍♀️", "man_dancing" to "🕺", 
        "woman_dancing" to "💃", "see_no_evil_monkey" to "🙈", "hear_no_evil_monkey" to "🙉", 
        "speak_no_evil_monkey" to "🙊", "clapping_hands" to "👏", "raised_hands" to "🙌", 
        "heart_hands" to "🫶", "palms_up_together" to "🤲", "handshake" to "🤝", "thumbs_up" to "👍", 
        "thumbs_down" to "👎", "oncoming_fist" to "👊", "raised_fist" to "✊", "left_facing_fist" to "🤛", 
        "right_facing_fist" to "🤜", "crossed_fingers" to "🤞", "victory_hand" to "✌️", 
        "love_you_gesture" to "🤟", "sign_of_the_horns" to "🤘", "vulcan_salute" to "🖖", 
        "ok_hand" to "👌", "pinching_hand" to "🤌", "pinched_fingers" to "🫰", "leftwards_hand" to "🫱", 
        "rightwards_hand" to "🫲", "backhand_index_pointing_up" to "👆", "backhand_index_pointing_down" to "👇", 
        "backhand_index_pointing_left" to "👈", "backhand_index_pointing_right" to "👉", "index_pointing_up" to "☝️", 
        "index_pointing_at_the_viewer" to "🫵", "raised_back_of_hand" to "🤚", "raised_hand" to "✋", 
        "hand_with_fingers_splayed" to "🖐️", "waving_hand" to "👋", "call_me_hand" to "🤙", 
        "hand_with_index_finger_and_thumb_crossed" to "🫰", "writing_hand" to "✍️", "nail_polish" to "💅", 
        "flexed_biceps" to "💪", "mechanical_arm" to "🦾", "mechanical_leg" to "🦿", "leg" to "🦵", 
        "foot" to "🦶", "footprints" to "👣", "tooth" to "🦷", "ear" to "👂", "ear_with_hearing_aid" to "🦻", 
        "nose" to "👃", "mouth" to "👄", "tongue" to "👅", "eyes" to "👀", "monkey_face" to "🐵", 
        "dog_face" to "🐶", "cat_face" to "🐱", "tiger_face" to "🐯", "leopard" to "🐆", 
        "horse_face" to "🐴", "zebra" to "🦓", "deer" to "🦌", "ox" to "🐂", "cow" to "🐄", 
        "pig_face" to "🐷", "pig_nose" to "🐽", "bison" to "🦬", "ram" to "🐏", "aries" to "♈", 
        "goat" to "🐐", "llama" to "🦙", "giraffe" to "🦒", "elephant" to "🐘", "rhinoceros" to "🦏", 
        "hippo" to "🦛", "mouse_face" to "🐭", "rabbit_face" to "🐰", "hamster" to "🐹", 
        "hedgehog" to "🦔", "bat" to "🦇", "bear" to "🐻", "polar_bear" to "🐻‍❄️", "koala" to "🐨", 
        "panda" to "🐼", "kangaroo" to "🦘", "raccoon" to "🦝", "bird" to "🐦", "baby_chick" to "🐤", 
        "front_facing_baby_chick" to "🐥", "hatching_chick" to "🐣", "chicken" to "🐔", "penguin" to "🐧", 
        "dove" to "🕊️", "eagle" to "🦅", "duck" to "🦆", "swan" to "🦢", "owl" to "🦉", "flamingo" to "🦩", 
        "peacock" to "🦚", "parrot" to "🦜", "frog" to "🐸", "turtle" to "🐢", "snake" to "🐍", 
        "t_rex" to "REX", "sauropod" to "🦕", "dragon_face" to "🐲", "spouting_whale" to "🐳", 
        "whale" to "🐋", "dolphin" to "🐬", "seal" to "🦭", "fish" to "🐟", "tropical_fish" to "🐠", 
        "blowfish" to "🐡", "shark" to "🦈", "octopus" to "🐙", "squid" to "🦑", "lobster" to "🦞", 
        "shrimp" to "🦐", "crab" to "🦀", "butterfly" to "🦋", "snail" to "🐌", "beetle" to "🪲", 
        "ant" to "🐜", "honeybee" to "🐝", "cricket" to "🦗", "lady_beetle" to "🐞", "spider" to "🕷️", 
        "spider_web" to "🕸️", "mosquito" to "🦟", "microbe" to "🦠", "blossom" to "🌼", 
        "cherry_blossom" to "🌸", "white_flower" to "💮", "rosette" to "🏵️", "rose" to "🌹", 
        "wilted_flower" to "🥀", "hibiscus" to "🌺", "sunflower" to "🌻", "tulip" to "🌷", 
        "seedling" to "🌱", "potted_plant" to "🪴", "evergreen_tree" to "🌲", "deciduous_tree" to "🌳", 
        "palm_tree" to "🌴", "cactus" to "🌵", "herb" to "🌿", "shamrock" to "☘️", "four_leaf_clover" to "🍀", 
        "maple_leaf" to "🍁", "fallen_leaf" to "🍂", "leaf_fluttering_in_wind" to "🍃", "mushroom" to "🍄", 
        "sun" to "☀️", "sun_with_face" to "🌞", "full_moon" to "🌕", "new_moon" to "🌑", 
        "full_moon_face" to "🌝", "last_quarter_moon_face" to "🌜", "first_quarter_moon_face" to "🌛", 
        "new_moon_face" to "🌚", "waxing_gibbous_moon" to "🌔", "waxing_crescent_moon" to "🌒", 
        "last_quarter_moon" to "🌗", "waning_crescent_moon" to "🌘", "first_quarter_moon" to "🌓", 
        "star" to "⭐", "glowing_star" to "🌟", "shooting_star" to "🌠", "sparkles" to "✨", 
        "cloud" to "☁️", "sun_behind_cloud" to "⛅", "cloud_with_lightning" to "🌩️", 
        "cloud_with_rain" to "🌧️", "cloud_with_lightning_and_rain" to "⛈️", "cloud_with_snow" to "🌨️", 
        "snowflake" to "❄️", "snowman" to "☃️", "snowman_without_snow" to "⛄", "fire" to "🔥", 
        "collision" to "💥", "sparkler" to "🎇", "fireworks" to "🎆", "rainbow" to "🌈", 
        "hot_springs" to "♨️", "banana" to "🍌", "strawberry" to "🍓", "kiwi_fruit" to "🥝", 
        "tomato" to "🍅", "coconut" to "🥥", "avocado" to "🥑", "broccoli" to "🥦", "leafy_green" to "🥬", 
        "pretzel" to "🥨", "bagel" to "🥯", "pancakes" to "🥞", "french_fries" to "🍟", 
        "hamburger" to "🍔", "pizza" to "🍕", "hot_dog" to "🌭", "sandwich" to "🥪", "taco" to "🌮", 
        "burrito" to "🌯", "stuffed_flatbread" to "🥙", "cooking" to "🍳", "bento_box" to "🍱", 
        "rice_ball" to "🍙", "rice_cracker" to "🍘", "sushi" to "🍣", "fish_cake_with_swirl" to "🍥", 
        "oden" to "🍢", "dango" to "🍡", "shortcake" to "🍰", "birthday_cake" to "🎂", "custard" to "🍮", 
        "lollipop" to "🍭", "chocolate_bar" to "🍫", "popcorn" to "🍿", "doughnut" to "🍩", 
        "cookie" to "🍪", "glass_of_milk" to "🥛", "hot_beverage" to "☕", "beverage_box" to "🧃", 
        "cup_with_straw" to "🥤", "bubble_tea" to "🧋", "wine_glass" to "🍷", "cocktail_glass" to "🍸", 
        "tropical_drink" to "🍹", "bottle_with_popping_cork" to "🍾", "pouring_liquid" to "🫗", 
        "meat_on_bone" to "🍖", "poultry_leg" to "🍗", "balloon" to "🎈", "party_popper" to "🎉", 
        "confetti_ball" to "🎊", "top_hat" to "🎩", "graduation_cap" to "🎓", "crown" to "👑", 
        "rosette" to "🏵️", "military_medal" to "🎖️", "trophy" to "🏆", "sports_medal" to "🏅", 
        "medal_1st" to "🥇", "medal_2nd" to "🥈", "medal_3rd" to "🥉", "basketball" to "🏀", 
        "video_game" to "🎮", "clapper_board" to "🎬", "performing_arts" to "🎭", "artist_palette" to "🎨", 
        "magic_wand" to "🪄", "megaphone" to "📣", "studio_microphone" to "🎙️", "microphone" to "🎤", 
        "telescope" to "🔭", "microscope" to "🔬", "light_bulb" to "💡", "toolbox" to "🧰", 
        "wrench" to "🔧", "gear" to "⚙️", "gem_stone" to "💎", "coin" to "🪙", "money_bag" to "💰", 
        "money_with_wings" to "💸", "credit_card" to "💳", "abacus" to "🧮", "laptop" to "💻", 
        "printer" to "🖨️", "keyboard" to "⌨️", "telephone" to "☎️", "telephone_receiver" to "📞", 
        "pager" to "📟", "fax_machine" to "📠", "television" to "📺", "camera" to "📷", 
        "newspaper" to "📰", "open_book" to "📖", "books" to "📚", "label" to "🏷️", "bookmark" to "🔖", 
        "ledger" to "📒", "file_folder" to "📁", "calendar" to "📅", "tear_off_calendar" to "📆", 
        "card_index_dividers" to "🗂️", "chart_increasing" to "📈", "chart_decreasing" to "📉", 
        "bar_chart" to "📊", "clipboard" to "📋", "pushpin" to "📌", "paperclip" to "📎", 
        "pencil" to "✏️", "memo" to "📝", "briefcase" to "💼", "purse" to "👛", "handbag" to "👜", 
        "luggage" to "🧳", "left_luggage" to "🛅", "identification_card" to "🪪", "shopping_bags" to "🛍️", 
        "shopping_cart" to "🛒", "ticket" to "🎫", "admission_tickets" to "🎟️", "military_helmet" to "🪖", 
        "coffin" to "⚰️", "urn" to "⚱️", "crystal_ball" to "🔮", "thermometer" to "🌡️", 
        "stethoscope" to "🩺", "syringe" to "💉", "pill" to "💊", "soap" to "🧼", "roll_of_paper" to "🧻", 
        "mirror_ball" to "🪩", "key" to "🔑", "old_key" to "🗝️", "locked_with_key" to "🔐", 
        "hammer_and_wrench" to "🛠️", "axe" to "🪓", "pick" to "⛏️", "shield" to "🛡️", 
        "bow_and_arrow" to "🏹", "boomerang" to "🪃", "ladder" to "🪜", "elevator" to "🛗", 
        "mirror" to "🪞", "window" to "🪟", "bed" to "🛏️", "couch_and_lamp" to "🛋️", "chair" to "🪑", 
        "toilet" to "🚽", "shower" to "🚿", "bathtub" to "🛁", "hourglass_done" to "⌛", 
        "hourglass_not_done" to "⏳", "watch" to "⌚", "alarm_clock" to "⏰", "compass" to "🧭", 
        "anchor" to "⚓", "rocket" to "🚀", "airplane" to "✈️", "ambulance" to "🚑", 
        "police_car" to "🚓", "taxi" to "🚕", "automobile" to "🚗", "locomotive" to "🚂", 
        "motor_boat" to "🚤", "sailboat" to "⛵", "ferry" to "⛴️", "desert_island" to "🏝️", 
        "camping" to "🏕️", "classical_building" to "🏛️", "house" to "🏠", "japanese_castle" to "🏰", 
        "office_building" to "🏢", "post_office" to "🏣", "hospital" to "🏥", "bank" to "🏦", 
        "hotel" to "🏨", "convenience_store" to "🏪", "school" to "🏫", "department_store" to "🏬", 
        "factory" to "🏭", "stadium" to "🏟️", "church" to "⛪", "mosque" to "🕌", "synagogue" to "🕍", 
        "hindu_temple" to "🛕", "kaaba" to "🕋", "shinto_shrine" to "⛩️", "national_park" to "🏞️", 
        "roller_coaster" to "🎢", "carousel_horse" to "🎠", "ferris_wheel" to "🎡", "red_heart" to "❤️", 
        "orange_heart" to "🧡", "yellow_heart" to "💛", "green_heart" to "💚", "blue_heart" to "💙", 
        "purple_heart" to "💜", "brown_heart" to "🤎", "black_heart" to "🖤", "white_heart" to "🤍", 
        "broken_heart" to "💔", "heart_on_fire" to "❤️‍🔥", "mending_heart" to "❤️‍🩹", 
        "heart_exclamation" to "❣", "two_hearts" to "💕", "revolving_hearts" to "💞", 
        "beating_heart" to "💓", "growing_heart" to "💗", "sparkling_heart" to "💖", 
        "heart_with_arrow" to "💘", "heart_with_ribbon" to "💝", "heart_decoration" to "💟", 
        "kiss_mark" to "💋", "love_letter" to "💌", "anger_symbol" to "💢", "sweat_drops" to "💦", 
        "dizzy" to "💫", "speech_balloon" to "💬", "thought_balloon" to "💭", "zzz" to "💤", 
        "check_mark" to "✔", "check_mark_button" to "✅", "check_box_with_check" to "☑", 
        "cross_mark" to "❌", "cross_mark_button" to "❎", "double_exclamation_mark" to "‼", 
        "exclamation_question_mark" to "⁉", "white_exclamation_mark" to "❕", "white_question_mark" to "❔", 
        "question_mark" to "❓", "hundred_points" to "💯", "no_one_under_eighteen" to "🔞", 
        "radioactive" to "☢", "biohazard" to "☣", "trident_emblem" to "🔱", "beginner_symbol" to "🔰", 
        "recycling_symbol" to "♻", "fleur_de_lis" to "⚜", "ophiuchus" to "⛎", "aries" to "♈", 
        "taurus" to "♉", "gemini" to "♊", "cancer" to "♋", "leo" to "♌", "virgo" to "♍", 
        "libra" to "♎", "scorpio" to "♏", "sagittarius" to "♐", "capricorn" to "♑", 
        "aquarius" to "♒", "pisces" to "♓", "id_button" to "🆔", "cl_button" to "🆑", 
        "sos_button" to "🆘", "ok_button" to "🆗", "up_button" to "🆙", "new_button" to "🆕", 
        "free_button" to "🆓", "cool_button" to "🆒", "top_arrow" to "🔝", "black_flag" to "🏴", 
        "white_flag" to "🏳️", "triangular_flag" to "🚩", "pirate_flag" to "🏴‍☠️", "chequered_flag" to "🏁"
    )

    init {
        setBackgroundColor(Color.parseColor("#FAFAFA")) 
        refreshSettings()
        setupFooter()
        setMode(KeyboardMode.ALPHA)
        setupSensors()
        startSmoothLoop()
    }

    fun refreshSettings() {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val newRevolver = prefs.getBoolean("revolver_mode", false)
        val newSystem = prefs.getBoolean("system_emojis", false)
        
        if (revolverMode != newRevolver || useSystemEmojiPref != newSystem) {
            revolverMode = newRevolver
            useSystemEmojiPref = newSystem
            setMode(currentMode) // Refresh keys to apply new settings
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val screenHeight = Resources.getSystem().displayMetrics.heightPixels
        val forcedHeight = (screenHeight * 0.4f).toInt()
        val newHeightSpec = MeasureSpec.makeMeasureSpec(forcedHeight, MeasureSpec.EXACTLY)
        super.onMeasure(widthMeasureSpec, newHeightSpec)
    }

    private fun setupFooter() {
        val footer = TextView(context)
        footer.text = "DBL CLICK POWER TO CHANGE MODE"
        footer.textSize = 9f
        footer.setTextColor(Color.parseColor("#BBBBBB"))
        footer.setTypeface(null, Typeface.BOLD)
        footer.letterSpacing = 0.2f
        val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        lp.bottomMargin = (15 * density).toInt()
        addView(footer, lp)
    }

    private fun getCharacterData(mode: KeyboardMode): List<KeyData> {
        val list = mutableListOf<KeyData>()
        when (mode) {
            KeyboardMode.ALPHA -> {
                list.add(KeyData("😃", "😃", isSwitch = true))
                val letters = ('A'..'Z').toList()
                val secs = listOf("1","2","3","4","5","6","7","8","9","0","!","@","#","$","%","^","&","*","(",")","-","=","+","_","?","/")
                letters.forEachIndexed { i, c -> 
                    val base = c.toString()
                    val display = when(currentTextCase) {
                        TextCase.LOWER -> base.lowercase()
                        TextCase.SENTENCE -> if (lastTypedKeyChar == null || lastTypedKeyChar == " ") base.uppercase() else base.lowercase()
                        TextCase.UPPER -> base.uppercase()
                    }
                    val typed = when(currentTextCase) {
                        TextCase.LOWER -> base.lowercase()
                        TextCase.SENTENCE -> display // Matches display for sentence case
                        TextCase.UPPER -> base.uppercase()
                    }
                    list.add(KeyData(display, typed, secs.getOrNull(i))) 
                }
                list.add(KeyData("123", "123", isSwitch = true))
            }
            KeyboardMode.SYMBOLS -> {
                list.add(KeyData("ABC", "ABC", isSwitch = true))
                val syms = listOf("1","2","3","4","5","6","7","8","9","0","!","@","#","$","%","&","*","(",")","?","+","-","=")
                syms.forEach { list.add(KeyData(it, it)) }
                list.add(KeyData("😃", "😃", isSwitch = true))
            }
            KeyboardMode.EMOJIS -> {
                list.add(KeyData("ABC", "ABC", isSwitch = true))
                val names = when(currentEmojiCategory) {
                    "Faces" -> listOf("grinning_face", "grinning_face_with_big_eyes", "grinning_face_with_smiling_eyes", "beaming_face_with_smiling_eyes", "grinning_squinting_face", "grinning_face_with_sweat", "rolling_on_the_floor_laughing", "face_with_tears_of_joy", "slightly_smiling_face", "upside_down_face", "winking_face", "smiling_face_with_smiling_eyes", "smiling_face_with_halo", "smiling_face_with_hearts", "smiling_face_with_heart_eyes", "star_struck", "face_blowing_a_kiss", "kissing_face", "smiling_face", "kissing_face_with_closed_eyes", "kissing_face_with_smiling_eyes", "smiling_face_with_tear", "face_savoring_food", "face_with_tongue", "winking_face_with_tongue", "squinting_face_with_tongue", "money_mouth_face", "hugging_face", "face_with_hand_over_mouth", "face_with_open_eyes_and_hand_over_mouth", "face_with_peeking_eye", "shushing_face", "thinking_face", "face_with_raised_eyebrow", "neutral_face", "expressionless_face", "face_without_mouth", "dotted_line_face", "face_in_clouds", "smirking_face", "unamused_face", "face_with_rolling_eyes", "grimacing_face", "face_exhaling", "lying_face", "relieved_face", "pensive_face", "sleepy_face", "drooling_face", "sleeping_face", "face_with_medical_mask", "face_with_thermometer", "face_with_head_bandage", "nauseated_face", "face_vomiting", "sneezing_face", "hot_face", "cold_face", "woozy_face", "face_with_spiral_eyes", "exploding_head", "cowboy_hat_face", "partying_face", "disguised_face", "smiling_face_with_sunglasses", "nerd_face", "face_with_monocle", "confused_face", "face_with_diagonal_mouth", "worried_face", "slightly_frowning_face", "frowning_face", "face_with_open_mouth", "hushed_face", "astonished_face", "flushed_face", "face_holding_back_tears", "pleading_face", "frowning_face_with_open_mouth", "anguished_face", "fearful_face", "anxious_face_with_sweat", "sad_but_relieved_face", "loudly_crying_face", "face_screaming_in_fear", "confounded_face", "persevering_face", "tired_face", "weary_face", "yawning_face", "face_with_steam_from_nose", "pouting_face", "angry_face", "face_with_symbols_on_mouth", "smiling_face_with_horns", "angry_face_with_horns", "skull", "skull_and_crossbones", "pile_of_poo", "clown_face", "ogre", "goblin", "ghost", "alien", "alien_monster", "robot", "zombie", "man_zombie", "woman_zombie", "vampire", "man_vampire", "woman_vampire", "santa_claus", "mrs_claus", "mx_claus", "baby", "old_woman", "man_teacher", "man_technologist", "woman_technologist", "man_health_worker", "woman_health_worker", "man_police_officer", "woman_police_officer", "man_shrugging", "woman_shrugging", "man_facepalming", "woman_facepalming", "man_dancing", "woman_dancing", "see_no_evil_monkey", "hear_no_evil_monkey", "speak_no_evil_monkey")
                    "Hands" -> listOf("clapping_hands", "raised_hands", "heart_hands", "palms_up_together", "handshake", "thumbs_up", "thumbs_down", "oncoming_fist", "raised_fist", "left_facing_fist", "right_facing_fist", "crossed_fingers", "victory_hand", "love_you_gesture", "sign_of_the_horns", "vulcan_salute", "ok_hand", "pinching_hand", "pinched_fingers", "leftwards_hand", "rightwards_hand", "backhand_index_pointing_up", "backhand_index_pointing_down", "backhand_index_pointing_left", "backhand_index_pointing_right", "index_pointing_up", "index_pointing_at_the_viewer", "raised_back_of_hand", "raised_hand", "hand_with_fingers_splayed", "waving_hand", "call_me_hand", "hand_with_index_finger_and_thumb_crossed", "writing_hand", "nail_polish", "flexed_biceps", "mechanical_arm", "mechanical_leg", "leg", "foot", "footprints", "tooth", "ear", "ear_with_hearing_aid", "nose", "mouth", "tongue", "eyes")
                    "Animals" -> listOf("monkey_face", "dog_face", "cat_face", "tiger_face", "leopard", "horse_face", "zebra", "deer", "ox", "cow", "pig_face", "pig_nose", "bison", "ram", "aries", "goat", "llama", "giraffe", "elephant", "rhinoceros", "hippo", "mouse_face", "rabbit_face", "hamster", "hedgehog", "bat", "bear", "polar_bear", "koala", "panda", "kangaroo", "raccoon", "bird", "baby_chick", "front_facing_baby_chick", "hatching_chick", "chicken", "penguin", "dove", "eagle", "duck", "swan", "owl", "flamingo", "peacock", "parrot", "frog", "turtle", "snake", "t_rex", "sauropod", "dragon_face", "spouting_whale", "whale", "dolphin", "seal", "fish", "tropical_fish", "blowfish", "shark", "octopus", "squid", "lobster", "shrimp", "crab", "butterfly", "snail", "beetle", "ant", "honeybee", "cricket", "lady_beetle", "spider", "spider_web", "mosquito", "microbe")
                    "Food" -> listOf("banana", "strawberry", "kiwi_fruit", "tomato", "coconut", "avocado", "broccoli", "leafy_green", "pretzel", "bagel", "pancakes", "french_fries", "hamburger", "pizza", "hot_dog", "sandwich", "taco", "burrito", "stuffed_flatbread", "cooking", "bento_box", "rice_ball", "rice_cracker", "sushi", "fish_cake_with_swirl", "oden", "dango", "shortcake", "birthday_cake", "custard", "lollipop", "chocolate_bar", "popcorn", "doughnut", "cookie", "glass_of_milk", "hot_beverage", "beverage_box", "cup_with_straw", "bubble_tea", "wine_glass", "cocktail_glass", "tropical_drink", "bottle_with_popping_cork", "pouring_liquid", "meat_on_bone", "poultry_leg")
                    "Nature" -> listOf("blossom", "cherry_blossom", "white_flower", "rosette", "rose", "wilted_flower", "hibiscus", "sunflower", "tulip", "seedling", "potted_plant", "evergreen_tree", "deciduous_tree", "palm_tree", "cactus", "herb", "shamrock", "four_leaf_clover", "maple_leaf", "fallen_leaf", "leaf_fluttering_in_wind", "mushroom", "sun", "sun_with_face", "full_moon", "new_moon", "full_moon_face", "last_quarter_moon_face", "first_quarter_moon_face", "new_moon_face", "waxing_gibbous_moon", "waxing_crescent_moon", "last_quarter_moon", "waning_crescent_moon", "first_quarter_moon", "star", "glowing_star", "shooting_star", "sparkles", "cloud", "sun_behind_cloud", "cloud_with_lightning", "cloud_with_rain", "cloud_with_lightning_and_rain", "cloud_with_snow", "snowflake", "snowman", "snowman_without_snow", "fire", "collision", "sparkler", "fireworks", "rainbow", "hot_springs")
                    "Objects" -> listOf("balloon", "party_popper", "confetti_ball", "top_hat", "graduation_cap", "crown", "rosette", "military_medal", "trophy", "sports_medal", "medal_1st", "medal_2nd", "medal_3rd", "basketball", "video_game", "clapper_board", "performing_arts", "artist_palette", "magic_wand", "megaphone", "studio_microphone", "microphone", "telescope", "microscope", "light_bulb", "toolbox", "wrench", "gear", "gem_stone", "coin", "money_bag", "money_with_wings", "credit_card", "abacus", "laptop", "printer", "keyboard", "telephone", "telephone_receiver", "pager", "fax_machine", "television", "camera", "newspaper", "open_book", "books", "label", "bookmark", "ledger", "file_folder", "calendar", "tear_off_calendar", "card_index_dividers", "chart_increasing", "chart_decreasing", "bar_chart", "clipboard", "pushpin", "paperclip", "pencil", "memo", "briefcase", "purse", "handbag", "luggage", "left_luggage", "identification_card", "shopping_bags", "shopping_cart", "ticket", "admission_tickets", "military_helmet", "coffin", "urn", "crystal_ball", "thermometer", "stethoscope", "syringe", "pill", "soap", "roll_of_paper", "mirror_ball", "key", "old_key", "locked_with_key", "hammer_and_wrench", "axe", "pick", "shield", "bow_and_arrow", "boomerang", "ladder", "elevator", "mirror", "window", "bed", "couch_and_lamp", "chair", "toilet", "shower", "bathtub", "hourglass_done", "hourglass_not_done", "watch", "alarm_clock", "compass", "anchor", "rocket", "airplane", "ambulance", "police_car", "taxi", "automobile", "locomotive", "motor_boat", "sailboat", "ferry", "desert_island", "camping", "classical_building", "house", "japanese_castle", "office_building", "post_office", "hospital", "bank", "hotel", "convenience_store", "school", "department_store", "factory", "stadium", "church", "mosque", "synagogue", "hindu_temple", "kaaba", "shinto_shrine", "national_park", "roller_coaster", "carousel_horse", "ferris_wheel")
                    "Hearts" -> listOf("red_heart", "orange_heart", "yellow_heart", "green_heart", "blue_heart", "purple_heart", "brown_heart", "black_heart", "white_heart", "broken_heart", "heart_on_fire", "mending_heart", "heart_exclamation", "two_hearts", "revolving_hearts", "beating_heart", "growing_heart", "sparkling_heart", "heart_with_arrow", "heart_with_ribbon", "heart_decoration", "kiss_mark", "love_letter", "anger_symbol", "sweat_drops", "dizzy", "speech_balloon", "thought_balloon", "zzz", "check_mark", "check_mark_button", "check_box_with_check", "cross_mark", "cross_mark_button", "double_exclamation_mark", "exclamation_question_mark", "white_exclamation_mark", "white_question_mark", "question_mark", "hundred_points", "no_one_under_eighteen", "radioactive", "biohazard", "trident_emblem", "beginner_symbol", "recycling_symbol", "fleur_de_lis", "ophiuchus", "aries", "taurus", "gemini", "cancer", "leo", "virgo", "libra", "scorpio", "sagittarius", "capricorn", "aquarius", "pisces", "id_button", "cl_button", "sos_button", "ok_button", "up_button", "new_button", "free_button", "cool_button", "top_arrow", "black_flag", "white_flag", "triangular_flag", "pirate_flag", "chequered_flag")
                    else -> emptyList()
                }
                names.forEach { n ->
                    val rid = context.resources.getIdentifier(n, "drawable", context.packageName)
                    if (rid != 0) list.add(KeyData(n, emojiMap[n] ?: "😀", emojiResId = rid))
                }
                list.add(KeyData("123", "123", isSwitch = true))
            }
        }
        return list
    }

    fun setMode(m: KeyboardMode) {
        currentMode = m
        currentKeyDataList = getCharacterData(m)
        setupKeys()
        
        targetScroll = (currentKeyDataList.size / 2f).coerceIn(0f, max(0f, currentKeyDataList.size - 1f))
        scrollPosition = targetScroll
    }

    fun onPowerDoubleClick() {
        when(currentMode) {
            KeyboardMode.ALPHA -> {
                currentTextCase = when(currentTextCase) {
                    TextCase.LOWER -> TextCase.SENTENCE
                    TextCase.SENTENCE -> TextCase.UPPER
                    TextCase.UPPER -> TextCase.LOWER
                }
                setMode(currentMode) // Refresh keys
            }
            KeyboardMode.EMOJIS -> {
                currentEmojiCategoryIndex = (currentEmojiCategoryIndex + 1) % emojiCategories.size
                currentEmojiCategory = emojiCategories[currentEmojiCategoryIndex]
                setMode(currentMode) // Refresh keys
            }
            else -> {}
        }
    }

    private fun setupKeys() {
        keys.forEach { removeView(it) }
        keys.clear()
        currentKeyDataList.forEachIndexed { i, d ->
            val k = KeyView(context, d.char, d.typedChar, d.secondary, d.isSwitch, d.emojiResId, useSystemEmojiPref)
            k.onKeyClick = { typeKeyAtIndex(i, false) }
            k.onKeyLongClick = { typeKeyAtIndex(i, true) }
            addView(k)
            keys.add(k)
        }
    }

    private fun startSmoothLoop() {
        post(object : Runnable {
            override fun run() {
                val s = 0.08f; val d = 0.94f
                val f = (targetScroll - scrollPosition) * s
                scrollVelocity = (scrollVelocity + f) * d
                scrollPosition += scrollVelocity
                
                // Looping for revolver mode
                if (revolverMode && currentKeyDataList.isNotEmpty()) {
                    if (scrollPosition < 0) {
                        scrollPosition += currentKeyDataList.size
                        targetScroll += currentKeyDataList.size
                    } else if (scrollPosition >= currentKeyDataList.size) {
                        scrollPosition -= currentKeyDataList.size
                        targetScroll -= currentKeyDataList.size
                    }
                }
                
                updateKeys()
                postDelayed(this, 10) 
            }
        })
    }

    private fun updateKeys() {
        val w = width.takeIf { it > 0 } ?: Resources.getSystem().displayMetrics.widthPixels
        val h = height.takeIf { it > 0 } ?: (Resources.getSystem().displayMetrics.heightPixels * 0.4f).toInt()
        val cx = w / 2f
        
        keys.forEachIndexed { i, k ->
            var dist = i - scrollPosition
            
            if (revolverMode && currentKeyDataList.isNotEmpty()) {
                // Circular shortest distance logic
                val count = currentKeyDataList.size
                if (dist > count / 2f) dist -= count
                if (dist < -count / 2f) dist += count
                
                val ad = abs(dist)
                
                // REVOLVER MODE: Rotating Circular cylinder physics
                val radius = 180f * density 
                
                // Angle based on distance from center/scroll position
                // We show ~10 keys in a full circle arc for best spacing
                val angle = dist * (2 * PI.toFloat() / 10f) 
                
                val tx = cx + radius * sin(angle)
                val ty = (h * 0.75f) - radius * cos(angle)
                
                // SHARP FOCUS ON GLOW
                val sc = 1f + exp(-(dist * dist) / 0.8).toFloat() * 0.22f
                val op = max(0.4f, 1f - ad * 0.2f)
                val ro = (dist * (360f / 10f))
                val zi = 100f - ad * 10f
                
                k.translationX = (tx - (k.keyOuterWidth / 2f))
                k.translationY = ty - (k.keyOuterHeight / 2f)
                
                val lp = (currentLongPressingIndex == i)
                val lt = (lastTypedKeyChar == currentKeyDataList.getOrNull(i)?.typedChar)
                k.setPhysics(sc, ro, op, zi, lp, false, lt)
                
                // GLOW EFFECT FOR FOCUS
                k.setGlow(ad < 0.5f || lp)
                k.visibility = View.VISIBLE
            } else {
                val ad = abs(dist)
                // CLASSIC WHEEL: Curved horizontal arc (MOVING)
                val yo = exp(-(dist * dist) / 8.0).toFloat() * (-180f * density)
                val sc = 1f + exp(-(dist * dist) / 1.2).toFloat() * 0.08f
                val op = max(0.92f, 1f - ad * 0.25f)
                val ro = -dist * 0.2f
                val zi = 100f - ad * 10f
                
                val iw = 52 * density; val tx = cx + (dist * iw)
                k.translationX = (tx - (k.keyOuterWidth / 2f)).roundToInt().toFloat()
                k.translationY = ((h * 0.85f) + yo - (k.keyOuterHeight / 2f)).roundToInt().toFloat()
                
                val lp = (currentLongPressingIndex == i)
                val lt = (lastTypedKeyChar == currentKeyDataList.getOrNull(i)?.typedChar)
                k.setPhysics(sc, ro, op, zi, lp, false, lt)
                k.setGlow(lp)
                k.visibility = if (op <= 0.05f) View.GONE else View.VISIBLE
            }
        }
    }

    fun setLongPressingKey(i: Int?) { currentLongPressingIndex = i }
    fun setLastTypedKey(c: String?) { lastTypedKeyChar = c; if (c != null) postDelayed({ lastTypedKeyChar = null }, 150) }

    fun typeKeyAtIndex(i: Int, lp: Boolean = false) {
        val d = currentKeyDataList.getOrNull(i) ?: return
        keys.getOrNull(i)?.triggerPop()
        if (d.isSwitch) {
            when (d.char) {
                "😃" -> setMode(KeyboardMode.EMOJIS)
                "123" -> setMode(KeyboardMode.SYMBOLS)
                "ABC" -> setMode(KeyboardMode.ALPHA)
            }
            return
        }
        val commit = if (lp && d.secondary != null) d.secondary else d.typedChar
        onKeyPressed?.invoke(commit)
        setLastTypedKey(commit)
    }

    fun typeCurrentKey(lp: Boolean = false) {
        val count = currentKeyDataList.size
        if (count == 0) return
        
        var bestIndex = 0
        var minAd = Float.MAX_VALUE
        
        for (i in 0 until count) {
            var dist = i - scrollPosition
            if (revolverMode) {
                if (dist > count / 2f) dist -= count
                if (dist < -count / 2f) dist += count
            }
            val ad = abs(dist)
            if (ad < minAd) {
                minAd = ad
                bestIndex = i
            }
        }
        typeKeyAtIndex(bestIndex, lp)
    }

    private fun setupSensors() {
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
                ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        rotationSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    override fun onSensorChanged(e: SensorEvent?) {
        e ?: return
        val t = if (e.sensor.type == Sensor.TYPE_ACCELEROMETER) e.values[0] * 0.04f else e.values[1] * 1.2f
        if (abs(t) > 0.01f) {
            targetScroll += t * 0.35f
            if (!revolverMode) {
                targetScroll = targetScroll.coerceIn(0f, max(0f, currentKeyDataList.size - 1f))
            }
        }
    }
    override fun onAccuracyChanged(s: Sensor?, a: Int) {}
}

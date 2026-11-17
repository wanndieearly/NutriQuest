package com.nutriquest.app

// Reference values are rounded to whole grams for the existing diary UI.
// A detection selects a reference, never a measured recipe or portion weight.
private const val USDA27 = "https://www.ars.usda.gov/ARSUserFiles/80400525/Data/SR27/reports/"
private const val USDA28 = "https://www.ars.usda.gov/ARSUserFiles/80400535/Data/SR/SR28/reports/"

internal val foodList = listOf(
    Food("steamed_rice", "Steamed rice", "🍚", 260, 5, 57, 1, "白飯", setOf("rice", "米飯", "白饭", "米饭")),
    Food("grilled_chicken", "Grilled chicken", "🍗", 248, 47, 0, 5, "烤雞", setOf("chicken", "雞肉", "烤鸡", "鸡肉")),
    Food("boiled_egg", "Boiled egg", "🥚", 78, 6, 1, 5, "烚蛋", setOf("egg", "eggs", "雞蛋", "鸡蛋")),
    Food("vegetable_salad", "Vegetable salad", "🥗", 80, 3, 12, 3, "沙律", setOf("salad", "沙拉")),
    Food("banana", "Banana", "🍌", 105, 1, 27, 0, "香蕉", setOf("bananas")),
    Food("oatmeal", "Oatmeal", "🥣", 180, 6, 30, 4, "麥皮", setOf("oats", "燕麥", "燕麦", "麦皮")),
    Food("tofu", "Tofu", "🫘", 115, 12, 3, 7, "豆腐"),
    Food("milk_tea", "Milk tea", "🧋", 200, 4, 30, 7, "奶茶", sweetSnack = true),
    Food("pizza", "Cheese pizza", "🍕", 266, 11, 33, 10, "芝士薄餅", setOf("pizza", "pizzas", "cheese pizza", "pizza slice", "薄餅", "披薩", "披萨", "薄饼"),
        100, 100, "usda_sr27_21299", USDA27 + "sr27fg21.pdf", "USDA 21299 · regular-crust cheese pizza"),
    Food("apple", "Apple", "🍎", 52, 0, 14, 0, "蘋果", setOf("apples", "苹果"),
        100, 100, "usda_sr28_09003", USDA28 + "sr28fg09.pdf", "USDA 09003 · raw apple with skin"),
    Food("orange", "Orange", "🍊", 47, 1, 12, 0, "橙", setOf("oranges"),
        100, 100, "usda_sr28_09200", USDA28 + "sr28fg09.pdf", "USDA 09200 · raw orange, edible portion"),
    Food("broccoli", "Broccoli", "🥦", 34, 3, 7, 0, "西蘭花", setOf("西兰花"),
        100, 100, "usda_sr28_11090", USDA28 + "sr28fg11.pdf", "USDA 11090 · raw broccoli"),
    Food("carrot", "Carrot", "🥕", 41, 1, 10, 0, "紅蘿蔔", setOf("carrots", "胡蘿蔔", "胡萝卜", "红萝卜"),
        100, 100, "usda_sr28_11124", USDA28 + "sr28fg11.pdf", "USDA 11124 · raw carrot"),
    Food("sandwich", "Ham & cheese sandwich", "🥪", 241, 14, 23, 11, "火腿芝士三文治", setOf("sandwich", "sandwiches", "三文治", "三明治"),
        100, 100, "usda_sr27_21116", USDA27 + "sr27fg21.pdf", "USDA 21116 · ham and cheese sandwich reference"),
    Food("hot_dog", "Hot dog", "🌭", 247, 11, 18, 15, "熱狗", setOf("hotdog", "hot dog", "hot dogs", "热狗"),
        100, 100, "usda_sr28_21118", USDA28 + "sr28fg21.pdf", "USDA 21118 · plain hot dog with bun"),
    Food("donut", "Glazed doughnut", "🍩", 384, 6, 53, 15, "冬甩", setOf("donut", "donuts", "doughnut", "doughnuts", "甜甜圈"),
        100, 51, "krispy_kreme_uk_2026_original_glazed",
        "https://www.krispykreme.co.uk/media/wysiwyg/Allergens/KK1_2025_Allergen_Nutrition_Book_220126_UK__Nutty_.pdf",
        "Krispy Kreme UK · Original Glazed reference", sweetSnack = true),
    Food("cake", "Madeira cake", "🍰", 364, 5, 58, 12, "蛋糕", setOf("cake", "cakes", "sponge cake"),
        100, 33, "tesco_330746826", "https://www.tesco.com/shop/en-GB/products/330746826",
        "Tesco Classic Madeira Cake reference", sweetSnack = true),
    Food("macaron", "Macaron", "🍬", 430, 9, 56, 18, "馬卡龍", setOf("macarons", "馬克龍", "马卡龙"),
        100, 15, "waitrose_553849", "https://www.waitrose.com/ecom/products/no1-12-macarons/553849-828653-828654",
        "Waitrose No.1 macaron selection reference", sweetSnack = true)
)

internal fun Food.names(): Set<String> = aliases + setOf(id, name, chineseName)

internal fun catalogFood(label: String): Food? {
    fun normalized(value: String) = value.trim().lowercase().replace('_', ' ').replace('-', ' ').replace(Regex("\\s+"), " ")
    val query = normalized(label)
    return foodList.firstOrNull { food -> food.names().any { normalized(it) == query } }
}

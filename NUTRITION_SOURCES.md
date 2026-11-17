# Nutrition reference catalog

Verified on 2026-11-14. Energy is kcal and macronutrients are grams per 100 g of edible food. Macronutrients are rounded to whole grams to match the existing diary model. Portions scale these reference values before rounding the meal total.

| Entry | kcal | Protein | Carbs | Fat | Initial serving weight | Reference |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| Cheese pizza | 266 | 11 | 33 | 10 | 100 g | [USDA SR27 21299, regular crust](https://www.ars.usda.gov/ARSUserFiles/80400525/Data/SR27/reports/sr27fg21.pdf) |
| Apple | 52 | 0 | 14 | 0 | 100 g | [USDA SR28 09003, raw with skin](https://www.ars.usda.gov/ARSUserFiles/80400535/Data/SR/SR28/reports/sr28fg09.pdf) |
| Orange | 47 | 1 | 12 | 0 | 100 g | [USDA SR28 09200, raw](https://www.ars.usda.gov/ARSUserFiles/80400535/Data/SR/SR28/reports/sr28fg09.pdf) |
| Broccoli | 34 | 3 | 7 | 0 | 100 g | [USDA SR28 11090, raw](https://www.ars.usda.gov/ARSUserFiles/80400535/Data/SR/SR28/reports/sr28fg11.pdf) |
| Carrot | 41 | 1 | 10 | 0 | 100 g | [USDA SR28 11124, raw](https://www.ars.usda.gov/ARSUserFiles/80400535/Data/SR/SR28/reports/sr28fg11.pdf) |
| Ham and cheese sandwich | 241 | 14 | 23 | 11 | 100 g | [USDA SR27 21116](https://www.ars.usda.gov/ARSUserFiles/80400525/Data/SR27/reports/sr27fg21.pdf) |
| Plain hot dog with bun | 247 | 11 | 18 | 15 | 100 g | [USDA SR28 21118](https://www.ars.usda.gov/ARSUserFiles/80400535/Data/SR/SR28/reports/sr28fg21.pdf) |
| Glazed doughnut | 384 | 6 | 53 | 15 | 51 g | [Krispy Kreme UK Original Glazed, 22 Jan 2026](https://www.krispykreme.co.uk/media/wysiwyg/Allergens/KK1_2025_Allergen_Nutrition_Book_220126_UK__Nutty_.pdf) |
| Madeira cake | 364 | 5 | 58 | 12 | 33 g | [Tesco Classic Madeira Cake](https://www.tesco.com/shop/en-GB/products/330746826) |
| Macaron | 430 | 9 | 56 | 18 | 15 g | [Waitrose No.1 12 Macarons, 180 g](https://www.waitrose.com/ecom/products/no1-12-macarons/553849-828653-828654) |

These are representative references. The camera cannot identify the exact product or recipe from a broad class such as pizza, cake or sandwich. It does not measure portion weight. Confirm the food type and enter the weight, or use **Edit nutrition** for a label-specific entry. A rounded zero is not a claim that a food contains no trace of that nutrient.

The original rice, chicken, egg, salad, banana, oatmeal, tofu and milk-tea values are the existing demo catalog, preserved for compatibility. They are labelled as demo servings and are not validated by this reference table.

Exports retain the selected grams per serving, actual scaled nutrition per serving, the reference weight, source ID and source URL. Manual overrides are marked `user_entered` and keep no claim to the original reference source. Mixed meals use the top-level source `mixed` while each item retains its provenance.

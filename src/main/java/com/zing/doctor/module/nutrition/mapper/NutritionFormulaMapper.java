package com.zing.doctor.module.nutrition.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 营养制剂配方字典（应用库 config_nutrition_formula，由 46 号升级脚本建立）。
 *
 * <p>默认数据源即应用库；{@code match_keywords} 为逗号分隔的医嘱名称命中词，
 * 任一命中即把该执行单映射到对应制剂密度。
 */
@Mapper
public interface NutritionFormulaMapper {

    @Select("SELECT formula_code AS formula_code, formula_name AS formula_name, "
            + "route_type AS route_type, energy_kcal_per_ml AS energy_kcal_per_ml, "
            + "protein_g_per_ml AS protein_g_per_ml, match_keywords AS match_keywords "
            + "FROM config_nutrition_formula WHERE status = 1")
    List<Map<String, Object>> selectActiveFormulas();
}

package com.gregtechceu.gtceu.data.lang;

import com.tterrag.registrate.providers.RegistrateLangProvider;

public class ToolLang {

    public static void init(RegistrateLangProvider provider) {
        initDeathMessages(provider);
        initToolInfo(provider);
    }

    private static void initDeathMessages(RegistrateLangProvider provider) {
        provider.add("death.attack.gtpm.heat", "%s was boiled alive");
        provider.add("death.attack.gtpm.frost", "%s explored cryogenics");
        provider.add("death.attack.gtpm.chemical", "%s had a chemical accident");
        provider.add("death.attack.gtpm.electric", "%s was electrocuted");
        provider.add("death.attack.gtpm.radiation", "%s glows with joy now");
        provider.add("death.attack.gtpm.turbine", "%s put their head into a turbine");
        provider.add("death.attack.gtpm.explosion", "%s exploded");
        provider.add("death.attack.gtpm.explosion.player", "%s exploded with help of %s");
        provider.add("death.attack.gtpm.heat.player", "%s was boiled alive by %s");
        provider.add("death.attack.gtpm.pickaxe", "%s got mined by %s");
        provider.add("death.attack.gtpm.shovel", "%s got dug up by %s");
        provider.add("death.attack.gtpm.axe", "%s has been chopped by %s");
        provider.add("death.attack.gtpm.hoe", "%s had their head tilled by %s");
        provider.add("death.attack.gtpm.hammer", "%s was squashed by %s");
        provider.add("death.attack.gtpm.mallet", "%s got hammered to death by %s");
        provider.add("death.attack.gtpm.mining_hammer", "%s was mistaken for Ore by %s");
        provider.add("death.attack.gtpm.spade", "%s got excavated by %s");
        provider.add("death.attack.gtpm.wrench", "%s gave %s a whack with the Wrench!");
        provider.add("death.attack.gtpm.file", "%s has been filed D for 'Dead' by %s");
        provider.add("death.attack.gtpm.crowbar", "%s lost half a life to %s");
        provider.add("death.attack.gtpm.screwdriver", "%s has screwed with %s for the last time!");
        provider.add("death.attack.gtpm.mortar", "%s was ground to dust by %s");
        provider.add("death.attack.gtpm.wire_cutter", "%s has cut the cable for the Life Support Machine of %s");
        provider.add("death.attack.gtpm.scythe", "%s had their soul taken by %s");
        provider.add("death.attack.gtpm.knife", "%s was gently poked by %s");
        provider.add("death.attack.gtpm.butchery_knife", "%s was butchered by %s");
        provider.add("death.attack.gtpm.drill_lv", "%s was drilled with 32V by %s");
        provider.add("death.attack.gtpm.drill_mv", "%s was drilled with 128V by %s");
        provider.add("death.attack.gtpm.drill_hv", "%s was drilled with 512V by %s");
        provider.add("death.attack.gtpm.drill_ev", "%s was drilled with 2048V by %s");
        provider.add("death.attack.gtpm.drill_iv", "%s was drilled with 8192V by %s");
        provider.add("death.attack.gtpm.chainsaw_lv", "%s was massacred by %s");
        provider.add("death.attack.gtpm.wrench_lv", "%s's pipes were loosened by %s");
        provider.add("death.attack.gtpm.wrench_hv", "%s's pipes were loosened by %s");
        provider.add("death.attack.gtpm.wrench_iv", "%s had a Monkey Wrench thrown into their plans by %s");
        provider.add("death.attack.gtpm.lv_buzzsaw", "%s got buzzed by %s");
        provider.add("death.attack.gtpm.screwdriver_lv", "%s had their screws removed by %s");

        provider.add("death.attack.gtpm.medical_condition.asbestosis", "%s got mesothelioma");
        provider.add("death.attack.gtpm.medical_condition.chemical_burns", "%s had a chemical accident");
        provider.add("death.attack.gtpm.medical_condition.poison",
                "%s forgot that poisonous materials are, in fact, poisonous");
        provider.add("death.attack.gtpm.medical_condition.silicosis",
                "%s didn't die of tuberculosis. it was silicosis.");
        provider.add("death.attack.gtpm.medical_condition.arsenicosis", "%s got arsenic poisoning");
        provider.add("death.attack.gtpm.medical_condition.berylliosis", "%s mined emeralds a bit too greedily");
        provider.add("death.attack.gtpm.medical_condition.carcinogen", "%s got leukemia");
        provider.add("death.attack.gtpm.medical_condition.irritant", "%s got a §n§lREALLY§r bad rash");
        provider.add("death.attack.gtpm.medical_condition.methanol_poisoning",
                "%s tried to drink moonshine during the prohibition");
        provider.add("death.attack.gtpm.medical_condition.nausea", "%s died of nausea");
        provider.add("death.attack.gtpm.medical_condition.none", "%s died of... nothing?");
        provider.add("death.attack.gtpm.medical_condition.weak_poison", "%s ate lead (or mercury!)");
        provider.add("death.attack.gtpm.medical_condition.carbon_monoxide_poisoning", "%s left the stove on");
    }

    private static void initToolInfo(RegistrateLangProvider provider) {}
}

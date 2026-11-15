package br.nobrevilas;

/** Salvo em <mundo>/nobrevilas/config.json */
public class Config {
    public String guardModId = "mr_villager_guards";      // Villager Guards (datapack-mod 26.3): aldeoes viram guardas
    public String guardEntity = "minecraft:skeleton";     // guarda do Villager Guards (esqueleto invisivel com equipamento)
    public String fallbackGuardEntity = "minecraft:iron_golem";
    public boolean useVillagerGuards = true;               // aldeoes da vila viram guardas do Villager Guards
    public String currencyItem = "minecraft:emerald";   // qualquer item (inclusive de outros mods): moeda do mundo
    public String beaconBase = "minecraft:iron_block";   // base da piramide do farol
    public boolean cheatsEnabled = true;                   // /vila admin (somente criativo/espectador)
    public int allyVillageCost = 15;                      // esmeraldas
    public int allyKingdomCost = 60;
    public boolean autoCapitalKit = true;                  // true: toda capital recebe sozinha a muralha do kit
    public int payDayTicks = 24000;
    public int raidIntervalTicks = 6000;
}

package com.example.sonicwavev4.ui.persetmode.modes

object Vision10m : PresetMode {
    override val id: String = "vision_10m"
    override val displayName: String = "视力模式 · 10分钟/周期"

    override val steps: List<Step> = parseStepsCsv(
        """
        # intensity01V,frequencyHz,durationSec
        30,20,30
        40,14,150
        30,16,30
        30,20,30
        40,30,30
        40,14,60
        30,12,30
        40,14,180
        30,20,30
        40,30,30
        """
    )
}

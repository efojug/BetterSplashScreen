package moe.efojug.bettersplashscreen.compat

import android.graphics.drawable.Drawable
import android.os.Build
import moe.efojug.bettersplashscreen.diagnostics.HookLog
import moe.efojug.bettersplashscreen.policy.ThemeEvidence
import moe.efojug.bettersplashscreen.runtime.SplashSession

class ColorOsThemeAdapter(private val log: HookLog) {
    private val evidence = ThemeEvidence()

    fun resolve(session: SplashSession): Drawable? {
        val icon = evidence.verified(Build.FINGERPRINT, session.vendorCandidate)
        if (icon == null) log.once(
            "theme_unverified",
            "event=capability name=desktop_theme status=unverified " + "reason=no_validated_firmware_profile fallback=launcher_component"
        )
        return icon
    }
}

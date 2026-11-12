package moe.efojug.bettersplashscreen.compat

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.UserHandle
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.function.Consumer
import java.util.function.IntSupplier

data class ShellBindings(
    val drawer: Class<*>,
    val view: Class<*>,
    val makeContent: Method,
    val buildShell: Method,
    val buildFramework: Method,
    val backgroundColor: Method,
    val vendorBackground: Method,
    val vendorIconProbe: Method?,
    val packageContextAsUser: Method,
    val iconView: Method,
    val drawerFields: Map<String, Field>,
    val shellFields: Map<String, Field>,
    val attrsFields: Map<String, Field>,
    val frameworkFields: Map<String, Field>,
    val infoFields: Map<String, Field>,
    val taskFields: Map<String, Field>,
    val windowlessFlag: Int,
) {
    val coreMethods: List<Method>
        get() = listOf(
            makeContent, buildShell, buildFramework, backgroundColor, vendorBackground
        )

    companion object {
        private const val ROOT = "com.android.wm.shell.startingsurface."

        @SuppressLint("PrivateApi")
        fun resolve(loader: ClassLoader): ShellBindings {
            val int = Int::class.javaPrimitiveType!!
            val bool = Boolean::class.javaPrimitiveType!!
            val void = Void.TYPE
            val drawer = loader.loadClass(ROOT + "SplashscreenContentDrawer")
            val shell = ExactMembers.oneClass(
                loader,
                ROOT + "SplashscreenContentDrawer\$SplashViewBuilder",
                ROOT + "SplashscreenContentDrawer\$StartingWindowViewBuilder"
            )
            val attrs =
                loader.loadClass(ROOT + "SplashscreenContentDrawer\$SplashScreenWindowAttrs")
            val vendor = loader.loadClass(ROOT + "OplusShellStartingWindowManager")
            val view = loader.loadClass("android.window.SplashScreenView")
            val framework = loader.loadClass("android.window.SplashScreenView\$Builder")
            val info = loader.loadClass("android.window.StartingWindowInfo")

            // Do not reuse ActivityRecord's numerically inverted splash/snapshot constants.
            mapOf(
                "NONE" to 0,
                "SPLASH_SCREEN" to 1,
                "SNAPSHOT" to 2,
                "SOLID_COLOR_SPLASH_SCREEN" to 3,
                "LEGACY_SPLASH_SCREEN" to 4,
                "WINDOWLESS" to 5
            ).forEach { (suffix, expected) ->
                val field = info.getField("STARTING_WINDOW_TYPE_$suffix")
                if (field.type != int || !Modifier.isStatic(field.modifiers) || field.getInt(null) != expected) {
                    throw BindingException(
                        "incompatible", "${field.toGenericString()} expected=$expected"
                    )
                }
            }
            val task = info.getField("taskInfo").type
            fun fields(owner: Class<*>, writable: Boolean, vararg entries: Pair<String, Class<*>>) =
                entries.associate { (name, type) ->
                    name to ExactMembers.field(
                        owner, name, type, writable
                    )
                }

            fun publicFields(owner: Class<*>, vararg entries: Pair<String, Class<*>>) =
                entries.associate { (name, type) ->
                    val field = owner.getField(name)
                    if (field.type != type || Modifier.isStatic(field.modifiers)) {
                        throw BindingException("incompatible", field.toGenericString())
                    }
                    name to field.apply { isAccessible = true }
                }

            val probe = try {
                ExactMembers.method(
                    vendor,
                    "getIconExt",
                    Drawable::class.java,
                    Context::class.java,
                    ActivityInfo::class.java,
                    int,
                    bool,
                    bool,
                    bool
                )
            } catch (_: BindingException) {
                null
            }
            val flagField = info.getField("TYPE_PARAMETER_WINDOWLESS")
            if (flagField.type != int || !Modifier.isStatic(flagField.modifiers)) {
                throw BindingException("incompatible", flagField.toGenericString())
            }
            return ShellBindings(
                drawer = drawer, view = view,
                makeContent = ExactMembers.method(
                    drawer,
                    "makeSplashScreenContentView",
                    view,
                    Context::class.java,
                    info,
                    int,
                    Consumer::class.java
                ),
                buildShell = ExactMembers.method(shell, "build", view),
                buildFramework = ExactMembers.method(framework, "build", view),
                backgroundColor = ExactMembers.method(
                    drawer,
                    "getBGColorFromCache",
                    int,
                    ActivityInfo::class.java,
                    IntSupplier::class.java
                ),
                vendorBackground = ExactMembers.method(
                    vendor, "setContentViewBackground", void, view, Drawable::class.java
                ),
                vendorIconProbe = probe,
                packageContextAsUser = ExactMembers.method(
                    Context::class.java,
                    "createPackageContextAsUser",
                    Context::class.java,
                    String::class.java,
                    int,
                    UserHandle::class.java
                ),
                iconView = ExactMembers.method(view, "getIconView", android.view.View::class.java),
                drawerFields = fields(
                    drawer,
                    false,
                    "mContext" to Context::class.java,
                    "mTmpAttrs" to attrs,
                    "mIconSize" to int
                ),
                shellFields = fields(
                    shell,
                    false,
                    "this\$0" to drawer,
                    "mContext" to Context::class.java,
                    "mActivityInfo" to ActivityInfo::class.java
                ) + fields(
                    shell,
                    true,
                    "mSuggestType" to int,
                    "mOverlayDrawable" to Drawable::class.java,
                    "mThemeColor" to int,
                    "mForceBigIcon" to bool,
                    "mIsSupportSplashScreenPreview" to bool,
                    "mFinalIconSize" to int
                ),
                attrsFields = fields(
                    attrs,
                    true,
                    "mWindowBgResId" to int,
                    "mWindowBgColor" to int,
                    "mIconBgColor" to int,
                    "mSplashScreenIcon" to Drawable::class.java,
                    "mBrandingImage" to Drawable::class.java
                ),
                frameworkFields = fields(
                    framework,
                    true,
                    "mIconSize" to int,
                    "mBackgroundColor" to int,
                    "mIconDrawable" to Drawable::class.java,
                    "mOverlayDrawable" to Drawable::class.java,
                    "mIconBackground" to Drawable::class.java,
                    "mBrandingDrawable" to Drawable::class.java
                ) + fields(
                    framework,
                    false,
                    "mSurfacePackage" to loader.loadClass("android.view.SurfaceControlViewHost\$SurfacePackage"),
                    "mParceledIconBitmap" to Bitmap::class.java
                ),
                infoFields = publicFields(
                    info,
                    "targetActivityInfo" to ActivityInfo::class.java,
                    "taskInfo" to task,
                    "startingWindowTypeParameter" to int
                ),
                taskFields = publicFields(
                    task,
                    "topActivityInfo" to ActivityInfo::class.java,
                    "taskId" to int,
                    "displayId" to int,
                    "userId" to int,
                    "topActivityType" to int,
                    "baseActivity" to android.content.ComponentName::class.java,
                    "origActivity" to android.content.ComponentName::class.java
                ),
                windowlessFlag = flagField.getInt(null),
            )
        }
    }

    fun signatures(): List<String> = listOfNotNull(
        makeContent,
        buildShell,
        buildFramework,
        backgroundColor,
        vendorBackground,
        vendorIconProbe,
        packageContextAsUser,
        iconView
    ).map { it.toGenericString() } + listOf(
        drawerFields, shellFields, attrsFields, frameworkFields, infoFields, taskFields
    ).flatMap { it.values }.map { it.toGenericString() }
}

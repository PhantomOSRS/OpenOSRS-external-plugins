import okhttp3.OkHttpClient
import okhttp3.Request
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.get
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Paths
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.*


open class BootstrapTask : DefaultTask() {

    private val EXTERNALS = listOf("chinautohop-plugin", "chinglassblow-plugin", "chinnmz-plugin", "chincursealch-plugin", "chingiantseaweed-plugin", "chinourania-plugin", "chinbirdhouse-plugin", "chinmta-plugin", "chinarceuuslibrary-plugin", "chintellyhoplog-plugin", "chinfarmrun-plugin", "chinamethyst-plugin", "chinagilitypyramid-plugin", "chinmahoganyhomes-plugin", "chinzeahrunecrafting-plugin", "chinanglerfish-plugin", "chinmonkfish-plugin", "chintreerun-plugin")

    private fun formatDate(date: Date?) = with(date ?: Date()) {
        SimpleDateFormat("yyyy-MM-dd").format(this)
    }

    private fun hash(file: ByteArray): String {
        return MessageDigest.getInstance("SHA-512").digest(file).fold("", { str, it -> str + "%02x".format(it) }).toUpperCase()
    }

    private fun getBootstrap(): JSONArray? {
        val client = OkHttpClient()

        val url = "https://raw.githubusercontent.com/Owain94/OpenOSRS-external-plugins-hosting/master/plugins.json"
        val request = Request.Builder()
                .url(url)
                .build()

        client.newCall(request).execute().use { response -> return JSONObject("{\"plugins\":${response.body!!.string()}}").getJSONArray("plugins") }
    }

    @TaskAction
    fun boostrap() {
        if (project == project.rootProject) {
            val bootstrapDir = File("${project.buildDir}/bootstrap")
            val bootstrapReleaseDir = File("${project.buildDir}/bootstrap/release")

            bootstrapDir.mkdirs()
            bootstrapReleaseDir.mkdirs()

            val plugins = ArrayList<JSONObject>()
            val baseBootstrap = getBootstrap() ?: throw RuntimeException("Base bootstrap is null!")

            project.subprojects.forEach {
                if (it.project.properties.containsKey("PluginName") && it.project.properties.containsKey("PluginDescription")) {
                    var pluginAdded = false
                    val plugin = it.project.tasks.get("jar").outputs.files.singleFile

                    val releases = ArrayList<JsonBuilder>()

                    releases.add(JsonBuilder(
                            "version" to it.project.version,
                            "requires" to "^1.0.0",
                            "date" to formatDate(Date()),
                            "url" to "https://github.com/Owain94/OpenOSRS-external-plugins-hosting/blob/master/release/${it.project.name}-${it.project.version}.jar?raw=true",
                            "sha512sum" to hash(plugin.readBytes())
                    ))

                    val pluginObject = JsonBuilder(
                            "name" to it.project.extra.get("PluginName"),
                            "id" to nameToId(it.project.extra.get("PluginName") as String),
                            "description" to it.project.extra.get("PluginDescription"),
                            "provider" to "Owain94",
                            "projectUrl" to "https://discord.gg/chinplugins",
                            "releases" to releases.toTypedArray()
                    ).jsonObject()

                    for (i in 0 until baseBootstrap.length()) {
                        val item = baseBootstrap.getJSONObject(i)

                        if (!item.get("id").equals(nameToId(it.project.extra.get("PluginName") as String))) {
                            continue
                        }

                        if (it.project.version.toString() in item.getJSONArray("releases").toString()) {
                            pluginAdded = true
                            plugins.add(item)
                            break
                        }

                        plugins.add(JsonMerger(arrayMergeMode = JsonMerger.ArrayMergeMode.MERGE_ARRAY).merge(item, pluginObject))
                        pluginAdded = true
                    }

                    if (!pluginAdded) {
                        plugins.add(pluginObject)
                    }

                    plugin.copyTo(Paths.get(bootstrapReleaseDir.toString(), "${it.project.name}-${it.project.version}.jar").toFile())
                }
            }

            for (i in 0 until baseBootstrap.length()) {
                val item = baseBootstrap.getJSONObject(i)

                if (!EXTERNALS.contains(item.get("id"))) {
                    continue
                }

                plugins.add(item)
            }

            File(bootstrapDir, "plugins.json").printWriter().use { out ->
                out.println(plugins.toString())
            }
        }
    }
}

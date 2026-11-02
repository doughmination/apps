package gay.doughmination.manager.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusTest {
    private val cert = "AB:CD:" + "00:".repeat(29) + "EF"
    private val app = ManifestApp(
        id = "system",
        packageName = "gay.doughmination.plural",
        name = "System",
        signingCertSha256 = listOf(cert),
        latest = Release(
            versionCode = 1010,
            versionName = "2.1.10",
            apkUrl = "https://example.com/system.apk",
            sha256 = "a".repeat(64),
            sizeBytes = 100,
            minSdk = 26,
        ),
    )
    private fun installed(code: Long, certs: Set<String> = setOf(normaliseDigest(cert))) =
        InstalledApp(app.packageName, code, "x", certs)

    @Test fun notInstalled() = assertEquals(AppStatus.NotInstalled, Status.of(app, null, 34))
    @Test fun upToDate() = assertEquals(AppStatus.UpToDate, Status.of(app, installed(1010), 34))
    @Test fun older() = assertEquals(AppStatus.UpdateAvailable, Status.of(app, installed(1009), 34))
    @Test fun oldPerAppVersionCodes() = assertEquals(AppStatus.UpdateAvailable, Status.of(app, installed(107), 34))
    @Test fun newer() = assertEquals(AppStatus.InstalledNewer, Status.of(app, installed(1011), 34))

    @Test fun foreignKey() =
        assertEquals(AppStatus.SignatureMismatch, Status.of(app, installed(1009, setOf("f".repeat(64))), 34))

    @Test fun tooOldAndroid() {
        val needs35 = app.copy(latest = app.latest.copy(minSdk = 35))
        assertEquals(AppStatus.Incompatible, Status.of(needs35, null, 34))
        assertEquals(AppStatus.Incompatible, Status.of(needs35, installed(1000), 34))
        // Already current: nothing to install, so it isn't "incompatible".
        assertEquals(AppStatus.UpToDate, Status.of(needs35, installed(1010), 34))
    }

    @Test fun installableStates() {
        assertTrue(Status.canInstall(AppStatus.NotInstalled))
        assertTrue(Status.canInstall(AppStatus.UpdateAvailable))
        assertFalse(Status.canInstall(AppStatus.UpToDate))
        assertFalse(Status.canInstall(AppStatus.SignatureMismatch))
        assertFalse(Status.canInstall(AppStatus.InstalledNewer))
        assertFalse(Status.canInstall(AppStatus.Incompatible))
    }

    @Test fun digestsCompareRegardlessOfFormat() =
        assertEquals("abcdef", normaliseDigest("AB:cd:EF"))
}

class ManifestParserTest {
    private fun manifest(schema: Int = 1, url: String = "https://x/a.apk", sha: String = "b".repeat(64)) = """
        {"schemaVersion":$schema,"extra":"ignored","apps":[{"id":"a","packageName":"p.a","name":"A",
        "signingCertSha256":["${"c".repeat(64)}"],
        "latest":{"versionCode":5,"versionName":"2.1.5","apkUrl":"$url","sha256":"$sha","sizeBytes":10}}]}
    """.trimIndent()

    @Test fun parses() {
        val m = ManifestParser.parse(manifest())
        assertEquals(1, m.apps.size)
        assertEquals("2.1.5", m.apps[0].latest.versionName)
    }

    @Test fun refusesUnknownSchema() {
        assertThrows(ManifestException::class.java) { ManifestParser.parse(manifest(schema = 2)) }
    }

    @Test fun refusesGarbage() {
        assertThrows(ManifestException::class.java) { ManifestParser.parse("<html>") }
    }

    @Test fun dropsInsecureOrMalformedEntries() {
        assertEquals(0, ManifestParser.parse(manifest(url = "http://x/a.apk")).apps.size)
        assertEquals(0, ManifestParser.parse(manifest(sha = "nothex")).apps.size)
    }
}

class HashesTest {
    @Test fun sha256OfKnownInput() = assertEquals(
        "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        Hashes.sha256("abc".byteInputStream()),
    )
}

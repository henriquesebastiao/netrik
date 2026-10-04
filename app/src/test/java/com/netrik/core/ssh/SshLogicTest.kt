package com.netrik.core.ssh

import com.jcraft.jsch.JSchException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.Base64

class SshLogicTest {

    private fun fixture(name: String) = File("src/test/resources/ssh/$name").readBytes()

    private val ed25519Pub = File("src/test/resources/ssh/ed25519.pub").readText().trim().split(" ")

    @Test
    fun `fingerprint matches ssh-keygen`() {
        val blob = Base64.getDecoder().decode(ed25519Pub[1])
        assertEquals("SHA256:KLlBLWCv4258dGyNPA4jGcUYGrznD6//mSwfhVWPIf8", HostKeys.fingerprint(blob))
        assertEquals("ssh-ed25519", HostKeys.typeOf(blob))
    }

    @Test
    fun `key type for display`() {
        assertEquals("ED25519", HostKeys.displayType("ssh-ed25519"))
        assertEquals("RSA", HostKeys.displayType("rsa-sha2-512"))
        assertEquals("ECDSA P-384", HostKeys.displayType("ecdsa-sha2-nistp384"))
        assertNull(HostKeys.typeOf(byteArrayOf(0, 0)))
        assertNull(HostKeys.typeOf(byteArrayOf(0, 0, 0, 50, 1)))
    }

    @Test
    fun `host key known, unknown or changed`() {
        val a = HostKey("ssh-ed25519", byteArrayOf(1, 2, 3))
        val same = HostKey("ssh-ed25519", byteArrayOf(1, 2, 3))
        val other = HostKey("ssh-ed25519", byteArrayOf(9, 9, 9))
        assertEquals(HostKeyStatus.Unknown, HostKeys.status(null, a))
        assertEquals(HostKeyStatus.Known, HostKeys.status(a, same))
        assertEquals(HostKeyStatus.Changed, HostKeys.status(a, other))
    }

    @Test
    fun `host id as in known_hosts`() {
        assertEquals("srv.lan", HostKeys.hostId("SRV.lan", 22))
        assertEquals("[192.168.0.7]:2222", HostKeys.hostId("192.168.0.7", 2222))
    }

    @Test
    fun `reads OpenSSH and PEM keys`() {
        assertEquals(KeyInspection.Valid("ED25519", encrypted = false), PrivateKeys.inspect(fixture("ed25519")))
        assertEquals(KeyInspection.Valid("RSA 3072", encrypted = false), PrivateKeys.inspect(fixture("rsa_pem")))
        assertEquals(KeyInspection.Valid("ECDSA P-256", encrypted = false), PrivateKeys.inspect(fixture("ecdsa")))
    }

    @Test
    fun `encrypted key needs the right passphrase`() {
        val enc = fixture("ed25519_enc")
        val inspected = PrivateKeys.inspect(enc) as KeyInspection.Valid
        assertTrue(inspected.encrypted)
        assertEquals(KeyInspection.WrongPassphrase, PrivateKeys.inspect(enc, "errada".toByteArray()))
        assertEquals(KeyInspection.Valid("ED25519", encrypted = true), PrivateKeys.inspect(enc, "netrik-teste".toByteArray()))
        val pem = fixture("rsa_pem_enc")
        assertTrue((PrivateKeys.inspect(pem) as KeyInspection.Valid).encrypted)
        assertEquals(KeyInspection.WrongPassphrase, PrivateKeys.inspect(pem, "errada".toByteArray()))
        assertEquals(KeyInspection.Valid("RSA 2048", encrypted = true), PrivateKeys.inspect(pem, "netrik-teste".toByteArray()))
    }

    @Test
    fun `public key or garbage is not a private key`() {
        assertEquals(KeyInspection.Invalid, PrivateKeys.inspect(fixture("ed25519.pub")))
        assertEquals(KeyInspection.Invalid, PrivateKeys.inspect("not a key".toByteArray()))
        assertEquals(KeyInspection.TooLarge, PrivateKeys.inspect(ByteArray(PrivateKeys.MAX_SIZE + 1)))
    }

    @Test
    fun `key file size`() {
        assertEquals("411 bytes", PrivateKeys.formatSize(411))
        assertEquals("3,2 KB", PrivateKeys.formatSize(3277, java.util.Locale.forLanguageTag("pt-BR")))
        assertEquals("3.2 KB", PrivateKeys.formatSize(3277, java.util.Locale.US))
    }

    private fun input(
        host: String = "192.168.0.7",
        port: String = "22",
        user: String = "root",
        auth: SshAuth = SshAuth.Password,
        password: Boolean = true,
        key: Boolean = false,
        stored: Boolean = false,
    ) = SshForm.Input(host, port, user, auth, password, key, stored)

    @Test
    fun `form validation`() {
        assertEquals(emptyMap<SshField, SshFieldError>(), SshForm.validate(input()))
        val empty = SshForm.validate(input(host = " ", port = "", user = "", password = false))
        assertEquals(SshFieldError.HostRequired, empty[SshField.Host])
        assertEquals(SshFieldError.PortInvalid, empty[SshField.Port])
        assertEquals(SshFieldError.UserRequired, empty[SshField.User])
        assertEquals(SshFieldError.PasswordRequired, empty[SshField.Password])
        assertEquals(SshFieldError.PortInvalid, SshForm.validate(input(port = "70000"))[SshField.Port])
        assertEquals(SshFieldError.KeyRequired, SshForm.validate(input(auth = SshAuth.Key))[SshField.Key])
        // Editing: a blank password keeps the saved one.
        assertEquals(emptyMap<SshField, SshFieldError>(), SshForm.validate(input(password = false, stored = true)))
    }

    @Test
    fun `hosts aceitos`() {
        assertTrue(SshForm.isValidHost("srv.company.com"))
        assertTrue(SshForm.isValidHost("fe80::1%wlan0"))
        assertTrue(SshForm.isValidHost("[2001:db8::7]"))
        assertFalse(SshForm.isValidHost("ssh://srv"))
        assertFalse(SshForm.isValidHost("srv 01"))
        assertEquals("2001:db8::7", SshForm.normalizeHost(" [2001:db8::7] "))
        assertEquals("srv-01", SshForm.displayName(" srv-01 ", "10.0.0.1"))
        assertEquals("10.0.0.1", SshForm.displayName("", "10.0.0.1"))
    }

    @Test
    fun `JSch failure classification`() {
        assertEquals(SshFailure.Timeout, SshConnector.classify(JSchException("timeout: socket is not established", SocketTimeoutException())))
        assertEquals(SshFailure.Refused, SshConnector.classify(JSchException("java.net.ConnectException", ConnectException("refused"))))
        assertEquals(SshFailure.AuthRejected, SshConnector.classify(JSchException("Auth fail for methods 'publickey,password'")))
        assertTrue(SshConnector.classify(JSchException("session is down")) is SshFailure.Other)
    }

    @Test
    fun `secrets wiped after use`() {
        val password = "segredo".toByteArray()
        val target = SshTarget("h", 22, "u", SshAuth.Password, password = password)
        target.wipe()
        assertTrue(password.all { it == 0.toByte() })
        assertFalse(target.toString().contains("segredo"))
    }
}

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
    fun `impressão digital igual à do ssh-keygen`() {
        val blob = Base64.getDecoder().decode(ed25519Pub[1])
        assertEquals("SHA256:KLlBLWCv4258dGyNPA4jGcUYGrznD6//mSwfhVWPIf8", HostKeys.fingerprint(blob))
        assertEquals("ssh-ed25519", HostKeys.typeOf(blob))
    }

    @Test
    fun `tipo da chave para exibição`() {
        assertEquals("ED25519", HostKeys.displayType("ssh-ed25519"))
        assertEquals("RSA", HostKeys.displayType("rsa-sha2-512"))
        assertEquals("ECDSA P-384", HostKeys.displayType("ecdsa-sha2-nistp384"))
        assertNull(HostKeys.typeOf(byteArrayOf(0, 0)))
        assertNull(HostKeys.typeOf(byteArrayOf(0, 0, 0, 50, 1)))
    }

    @Test
    fun `chave do host conhecida, desconhecida ou alterada`() {
        val a = HostKey("ssh-ed25519", byteArrayOf(1, 2, 3))
        val same = HostKey("ssh-ed25519", byteArrayOf(1, 2, 3))
        val other = HostKey("ssh-ed25519", byteArrayOf(9, 9, 9))
        assertEquals(HostKeyStatus.Unknown, HostKeys.status(null, a))
        assertEquals(HostKeyStatus.Known, HostKeys.status(a, same))
        assertEquals(HostKeyStatus.Changed, HostKeys.status(a, other))
    }

    @Test
    fun `identificador do host como no known_hosts`() {
        assertEquals("srv.lan", HostKeys.hostId("SRV.lan", 22))
        assertEquals("[192.168.0.7]:2222", HostKeys.hostId("192.168.0.7", 2222))
    }

    @Test
    fun `lê chaves OpenSSH e PEM`() {
        assertEquals(KeyInspection.Valid("ED25519", encrypted = false), PrivateKeys.inspect(fixture("ed25519")))
        assertEquals(KeyInspection.Valid("RSA 3072", encrypted = false), PrivateKeys.inspect(fixture("rsa_pem")))
        assertEquals(KeyInspection.Valid("ECDSA P-256", encrypted = false), PrivateKeys.inspect(fixture("ecdsa")))
    }

    @Test
    fun `chave cifrada exige a senha certa`() {
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
    fun `chave pública ou lixo não é chave privada`() {
        assertEquals(KeyInspection.Invalid, PrivateKeys.inspect(fixture("ed25519.pub")))
        assertEquals(KeyInspection.Invalid, PrivateKeys.inspect("não é uma chave".toByteArray()))
        assertEquals(KeyInspection.TooLarge, PrivateKeys.inspect(ByteArray(PrivateKeys.MAX_SIZE + 1)))
    }

    @Test
    fun `tamanho do arquivo de chave`() {
        assertEquals("411 bytes", PrivateKeys.formatSize(411))
        assertEquals("3,2 KB", PrivateKeys.formatSize(3277))
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
    fun `validação do formulário`() {
        assertEquals(emptyMap<SshField, String>(), SshForm.validate(input()))
        val empty = SshForm.validate(input(host = " ", port = "", user = "", password = false))
        assertEquals("Informe o hostname ou IP", empty[SshField.Host])
        assertEquals("Inválida", empty[SshField.Port])
        assertEquals("Informe o usuário", empty[SshField.User])
        assertEquals("Informe a senha", empty[SshField.Password])
        assertEquals("Inválida", SshForm.validate(input(port = "70000"))[SshField.Port])
        assertEquals("Selecione um arquivo de chave", SshForm.validate(input(auth = SshAuth.Key))[SshField.Key])
        // Edição: senha em branco mantém a salva.
        assertEquals(emptyMap<SshField, String>(), SshForm.validate(input(password = false, stored = true)))
    }

    @Test
    fun `hosts aceitos`() {
        assertTrue(SshForm.isValidHost("srv.empresa.com"))
        assertTrue(SshForm.isValidHost("fe80::1%wlan0"))
        assertTrue(SshForm.isValidHost("[2001:db8::7]"))
        assertFalse(SshForm.isValidHost("ssh://srv"))
        assertFalse(SshForm.isValidHost("srv 01"))
        assertEquals("2001:db8::7", SshForm.normalizeHost(" [2001:db8::7] "))
        assertEquals("srv-01", SshForm.displayName(" srv-01 ", "10.0.0.1"))
        assertEquals("10.0.0.1", SshForm.displayName("", "10.0.0.1"))
    }

    @Test
    fun `classificação das falhas do JSch`() {
        assertEquals(SshFailure.Timeout, SshConnector.classify(JSchException("timeout: socket is not established", SocketTimeoutException())))
        assertEquals(SshFailure.Refused, SshConnector.classify(JSchException("java.net.ConnectException", ConnectException("refused"))))
        assertEquals(SshFailure.AuthRejected, SshConnector.classify(JSchException("Auth fail for methods 'publickey,password'")))
        assertTrue(SshConnector.classify(JSchException("session is down")) is SshFailure.Other)
    }

    @Test
    fun `segredos apagados depois do uso`() {
        val password = "segredo".toByteArray()
        val target = SshTarget("h", 22, "u", SshAuth.Password, password = password)
        target.wipe()
        assertTrue(password.all { it == 0.toByte() })
        assertFalse(target.toString().contains("segredo"))
    }
}

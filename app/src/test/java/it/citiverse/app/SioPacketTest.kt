package it.citiverse.app

import it.citiverse.app.data.NotificationItem
import it.citiverse.app.push.NodeBBEvents
import it.citiverse.app.push.SioPacket
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SioPacketTest {

    @Test
    fun openPacketReadsServerKeepalive() {
        // pacchetto reale ricevuto da citiverse.it
        val p = SioPacket.parse("""0{"sid":"gxxiJJBshHz509VGAAIK","upgrades":["websocket"],"pingInterval":25000,"pingTimeout":20000,"maxPayload":1000000}""")
        assertEquals(SioPacket.Open(25_000, 20_000), p)
    }

    @Test
    fun pingAndConnect() {
        assertEquals(SioPacket.Ping, SioPacket.parse("2"))
        assertEquals(SioPacket.Connected, SioPacket.parse("""40{"sid":"sei20s7_khui_NH5AAIL"}"""))
    }

    @Test
    fun eventWithArgs() {
        val p = SioPacket.parse("""42["checkSession",0]""")
        assertTrue(p is SioPacket.Event)
        p as SioPacket.Event
        assertEquals("checkSession", p.name)
        assertEquals("0", (p.args[0] as JsonPrimitive).content)
    }

    @Test
    fun notificationEvent() {
        val p = SioPacket.parse("""42["event:new_notification",{"nid":"new_post:tid:5","type":"new-reply","tid":5}]""") as SioPacket.Event
        assertEquals("event:new_notification", p.name)
        assertEquals("new-reply", ((p.args[0] as JsonObject)["type"] as JsonPrimitive).content)
    }

    @Test
    fun nodebb4WrappedNotification() {
        // da NodeBB 4: emit('event:new_notification', { notification, unreadCount })
        val p = SioPacket.parse(
            """42["event:new_notification",{"notification":{"nid":"new_post:tid:5:pid:9:uid:3","type":"new-reply","tid":5,"pid":9,"bodyShort":"[[notifications:user-posted-to, Mario, Ciao]]","path":"/post/9","datetime":1790000000000},"unreadCount":3}]"""
        ) as SioPacket.Event
        val data = NodeBBEvents.newNotification(p.args[0])
        assertEquals(3, data.unreadCount)
        val item = NotificationItem.from(data.notification!!)!!
        assertEquals("new_post:tid:5:pid:9:uid:3", item.nid)
        assertEquals("5", item.tid)
        assertEquals("9", item.pid)
    }

    @Test
    fun legacyBareNotification() {
        val p = SioPacket.parse("""42["event:new_notification",{"nid":"abc","type":"mention"}]""") as SioPacket.Event
        val data = NodeBBEvents.newNotification(p.args[0])
        assertEquals("abc", NotificationItem.from(data.notification!!)!!.nid)
    }

    @Test
    fun ackWithError() {
        val p = SioPacket.parse("""431[{"message":"[[error:invalid-event, meta.getServerTime]]"}]""")
        assertTrue(p is SioPacket.Ack)
        assertEquals(1, (p as SioPacket.Ack).id)
    }

    @Test
    fun encodeEventWithAck() {
        val s = SioPacket.encodeEvent("notifications.markRead", listOf(JsonPrimitive("abc")), 7)
        assertEquals("""427["notifications.markRead","abc"]""", s)
    }
}

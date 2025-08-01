package eu.rekawek.coffeegb.emulator.session

import eu.rekawek.coffeegb.Gameboy
import eu.rekawek.coffeegb.controller.Button
import eu.rekawek.coffeegb.controller.Joypad
import eu.rekawek.coffeegb.events.EventBus
import eu.rekawek.coffeegb.swing.emulator.session.Input
import eu.rekawek.coffeegb.swing.emulator.session.LinkedSession
import eu.rekawek.coffeegb.swing.emulator.session.LinkedSession.LocalButtonStateEvent
import eu.rekawek.coffeegb.swing.emulator.session.LinkedSession.RemoteButtonStateEvent
import eu.rekawek.coffeegb.swing.emulator.session.StateHistory.GameboyJoypadPressEvent
import eu.rekawek.coffeegb.swing.events.register
import eu.rekawek.coffeegb.testing.RandomJoypad
import org.junit.Test
import java.nio.file.Paths
import kotlin.test.assertEquals

class LinkedSessionTest {

  @Test
  fun localChangesAreReplayedOnRewind() {
    val eventBus = EventBus()
    val buttons = mutableListOf<Joypad.JoypadPressEvent>()
    eventBus.register<Joypad.JoypadPressEvent> { buttons += it }
    val sut = LinkedSession(eventBus, ROM, ROM_BYTES, null, null)
    val randomJoypad = RandomJoypad(eventBus)
    val tickRunnable = sut.init()
    repeat(Gameboy.TICKS_PER_FRAME * 100) {
      tickRunnable.run()
      if (it > Gameboy.TICKS_PER_FRAME) {
        randomJoypad.tick()
      }
    }
    repeat(Gameboy.TICKS_PER_FRAME) { tickRunnable.run() }

    val expectedButtons = buttons.toList()
    buttons.clear()

    sut.stateHistory!!.debugEventBus =
        EventBus().also { eb ->
          eb.register<GameboyJoypadPressEvent> { e ->
            if (e.gameboy == 0) {
              buttons += Joypad.JoypadPressEvent(e.button, e.tick)
            }
          }
        }

    eventBus.post(RemoteButtonStateEvent(1, Input(listOf(Button.UP), emptyList())))
    repeat(Gameboy.TICKS_PER_FRAME * 5) { tickRunnable.run() }

    val actualButtons = buttons.toList()

    assertJoypadEventsEqual(expectedButtons, actualButtons)
  }

  @Test
  fun remoteChangesAreSentCorrectly() {
    val eventBus1 = EventBus()
    val buttons1 = mutableListOf<Joypad.JoypadPressEvent>()
    eventBus1.register<Joypad.JoypadPressEvent> { buttons1 += it }
    val sut1 = LinkedSession(eventBus1, ROM, ROM_BYTES, null, null)
    val randomJoypad = RandomJoypad(eventBus1)
    val tickRunnable1 = sut1.init()

    val eventBus2 = EventBus()
    val buttons2 = mutableListOf<Joypad.JoypadPressEvent>()
    val sut2 = LinkedSession(eventBus2, ROM, ROM_BYTES, null, null)
    val tickRunnable2 = sut2.init()
    sut2.stateHistory!!.debugEventBus =
      EventBus().also { eb ->
        eb.register<GameboyJoypadPressEvent> { e ->
          if (e.gameboy == 1) {
            buttons2 += Joypad.JoypadPressEvent(e.button, e.tick)
          }
        }
      }

    eventBus1.register<LocalButtonStateEvent> {
      eventBus2.post(RemoteButtonStateEvent(it.frame, it.input))
    }

    repeat(Gameboy.TICKS_PER_FRAME * 100) {
      tickRunnable1.run()
      tickRunnable2.run()
      if (it > Gameboy.TICKS_PER_FRAME) {
        randomJoypad.tick()
      }
    }
    repeat(Gameboy.TICKS_PER_FRAME * 5) {
      tickRunnable1.run()
      tickRunnable2.run()
    }

    assertJoypadEventsEqual(buttons1, buttons2)
  }

  @Test
  fun twoWayCommunicationProducesSameResults() {
    val eventBus1 = EventBus()
    val buttons1 = mutableListOf<Joypad.JoypadPressEvent>()
    eventBus1.register<Joypad.JoypadPressEvent> { buttons1 += it }
    val sut1 = LinkedSession(eventBus1, ROM, ROM_BYTES, null, null)
    val randomJoypad1 = RandomJoypad(eventBus1)
    val tickRunnable1 = sut1.init()

    val eventBus2 = EventBus()
    val buttons2 = mutableListOf<Joypad.JoypadPressEvent>()
    val sut2 = LinkedSession(eventBus2, ROM, ROM_BYTES, null, null)
    val randomJoypad2 = RandomJoypad(eventBus2)
    val tickRunnable2 = sut2.init()
    sut2.stateHistory!!.debugEventBus =
      EventBus().also { eb ->
        eb.register<GameboyJoypadPressEvent> { e ->
          if (e.gameboy == 1) {
            buttons2 += Joypad.JoypadPressEvent(e.button, e.tick)
          }
        }
      }

    eventBus1.register<LocalButtonStateEvent> {
      eventBus2.post(RemoteButtonStateEvent(it.frame, it.input))
    }
    eventBus2.register<LocalButtonStateEvent> {
      eventBus1.post(RemoteButtonStateEvent(it.frame, it.input))
    }

    repeat(Gameboy.TICKS_PER_FRAME * 100) {
      tickRunnable1.run()
      tickRunnable2.run()
      if (it > Gameboy.TICKS_PER_FRAME) {
        randomJoypad1.tick()
        randomJoypad2.tick()
      }
    }
    repeat(Gameboy.TICKS_PER_FRAME * 5) {
      tickRunnable1.run()
      tickRunnable2.run()
    }

    assertJoypadEventsEqual(buttons1, buttons2)
  }

  private companion object {
    val ROM = Paths.get("src/test/resources/roms/blargg", "cpu_instrs.gb").toFile()

    val ROM_BYTES = ROM.readBytes()

    fun assertJoypadEventsEqual(
        expectedButtons: List<Joypad.JoypadPressEvent>,
        actualButtons: List<Joypad.JoypadPressEvent>
    ) {
      val ticks =
          (expectedButtons.map { it.tick }.toSet() + actualButtons.map { it.tick() }.toSet())
              .toList()
              .sorted()
      for (t in ticks) {
        val exp = expectedButtons.filter { it.tick == t }.map { it.button }.sorted()
        val act = actualButtons.filter { it.tick == t }.map { it.button }.sorted()
        assertEquals(exp, act, "At tick $t, frame ${t/Gameboy.TICKS_PER_FRAME}")
      }
    }
  }
}

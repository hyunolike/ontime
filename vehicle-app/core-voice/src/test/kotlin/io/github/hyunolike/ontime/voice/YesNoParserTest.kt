package io.github.hyunolike.ontime.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class YesNoParserTest {
    @Test
    fun `수락 표현을 알아듣는다`() {
        listOf("그렇게 해 줘", "그렇게 해줘.", "응", "네", "어 바꿔 줘", "좋아", "그래 그렇게 하자", "오케이", "부탁해")
            .forEach { assertEquals(true, YesNoParser.parse(it), it) }
    }

    @Test
    fun `거절 표현을 알아듣는다`() {
        listOf("아니", "아니요", "괜찮아", "됐어", "그대로 가", "바꾸지 마", "하지 마", "싫어", "아니 그렇게 하지 마")
            .forEach { assertEquals(false, YesNoParser.parse(it), it) }
    }

    @Test
    fun `수락과 거절이 섞이면 거절로 본다`() {
        assertEquals(false, YesNoParser.parse("응 아니 그대로 가"))
    }

    @Test
    fun `관계없는 말과 빈 말은 모른다고 답한다`() {
        listOf("", "   ", "오늘 날씨 어때", "반응이 느리네", "네비 꺼 줘").forEach { assertNull(YesNoParser.parse(it), it) }
    }
}

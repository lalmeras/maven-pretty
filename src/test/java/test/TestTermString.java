package test;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import likide.pretty.PrettyEventSpy;

public class TestTermString {

	@Test
	void testSimple() {
		Assertions.assertThat(PrettyEventSpy.ellipsize("short", 10)).isEqualTo("short");
		Assertions.assertThat(PrettyEventSpy.ellipsize("short", 5)).isEqualTo("short");
		Assertions.assertThat(PrettyEventSpy.ellipsize("short", 4)).isEqualTo("shor");
	}

	@Test
	void testEscape() {
		Assertions.assertThat(PrettyEventSpy.ellipsize("\033[1mshort\033[0m", 10)).isEqualTo("\033[1mshort\033[0m");
		Assertions.assertThat(PrettyEventSpy.ellipsize("\033[1mshort\033[0m", 5)).isEqualTo("\033[1mshort\033[0m");
		Assertions.assertThat(PrettyEventSpy.ellipsize("\033[1mshort\033[0m", 4)).isEqualTo("\033[1mshor\033[0m");
	}

	@Test
	void testEscapeTwo() {
		Assertions.assertThat(PrettyEventSpy.ellipsize("\033[1mshort\033[0m \033[1mshort\033[0m", 15)).isEqualTo("\033[1mshort\033[0m \033[1mshort\033[0m");
		Assertions.assertThat(PrettyEventSpy.ellipsize("\033[1mshort\033[0m \033[1mshort\033[0m", 11)).isEqualTo("\033[1mshort\033[0m \033[1mshort\033[0m");
		Assertions.assertThat(PrettyEventSpy.ellipsize("\033[1mshort\033[0m \033[1mshort\033[0m", 10)).isEqualTo("\033[1mshort\033[0m \033[1mshor\033[0m");
		Assertions.assertThat(PrettyEventSpy.ellipsize("\033[1mshort\033[0m \033[1mshort\033[0m", 6)).isEqualTo("\033[1mshort\033[0m ");
		Assertions.assertThat(PrettyEventSpy.ellipsize("\033[1mshort\033[0m \033[1mshort\033[0m", 5)).isEqualTo("\033[1mshort\033[0m");
		Assertions.assertThat(PrettyEventSpy.ellipsize("\033[1mshort\033[0m \033[1mshort\033[0m", 4)).isEqualTo("\033[1mshor\033[0m");
	}

	@Test
	void testSuffixSimple() {
		Assertions.assertThat(PrettyEventSpy.ellipsize("short", 10, ">", 1)).isEqualTo("short");
		Assertions.assertThat(PrettyEventSpy.ellipsize("short", 5, ">", 1)).isEqualTo("short");
		Assertions.assertThat(PrettyEventSpy.ellipsize("short", 4, ">", 1)).isEqualTo("sho>");
	}

	@Test
	void testSuffixEscape() {
		Assertions.assertThat(PrettyEventSpy.ellipsize("\033[1mshort\033[0m", 10, ">", 1)).isEqualTo("\033[1mshort\033[0m");
		Assertions.assertThat(PrettyEventSpy.ellipsize("\033[1mshort\033[0m", 5, ">", 1)).isEqualTo("\033[1mshort\033[0m");
		Assertions.assertThat(PrettyEventSpy.ellipsize("\033[1mshort\033[0m", 4, ">", 1)).isEqualTo("\033[1msho\033[0m>");
	}

	@Test
	void testSuffixEscapeTwo() {
		Assertions.assertThat(PrettyEventSpy.ellipsize("\033[1mshort\033[0m \033[1mshort\033[0m", 15, ">", 1)).isEqualTo("\033[1mshort\033[0m \033[1mshort\033[0m");
		Assertions.assertThat(PrettyEventSpy.ellipsize("\033[1mshort\033[0m \033[1mshort\033[0m", 11, ">", 1)).isEqualTo("\033[1mshort\033[0m \033[1mshort\033[0m");
		Assertions.assertThat(PrettyEventSpy.ellipsize("\033[1mshort\033[0m \033[1mshort\033[0m", 10, ">", 1)).isEqualTo("\033[1mshort\033[0m \033[1msho\033[0m>");
		Assertions.assertThat(PrettyEventSpy.ellipsize("\033[1mshort\033[0m \033[1mshort\033[0m", 6, ">", 1)).isEqualTo("\033[1mshort\033[0m>");
		Assertions.assertThat(PrettyEventSpy.ellipsize("\033[1mshort\033[0m \033[1mshort\033[0m", 5, ">", 1)).isEqualTo("\033[1mshor\033[0m>");
		Assertions.assertThat(PrettyEventSpy.ellipsize("\033[1mshort\033[0m \033[1mshort\033[0m", 4, ">", 1)).isEqualTo("\033[1msho\033[0m>");
	}

}

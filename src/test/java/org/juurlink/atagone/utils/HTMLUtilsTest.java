package org.juurlink.atagone.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;

import org.junit.Test;

public class HTMLUtilsTest {

	@Test
	@SuppressWarnings("ConstantConditions")
	public void testGetDiagnosticValueByLabel() throws Exception {
		final String html = "            <div class=\"form-group no-border-top\">\n" +
			"                <label class=\"col-xs-6 control-label\">Apparaat</label>\n" +
			"                <div class=\"col-xs-6\">\n" +
			"                    <p class=\"form-control-static\">6808-1401-3109_15-30-001-555</p>\n" +
			"                </div>\n" +
			"            </div>\n" +
			"            <div class=\"form-group\">\n" +
			"                <label class=\"col-xs-6 control-label\">Apparaat alias</label>\n" +
			"                <div class=\"col-xs-6\">\n" +
			"                    <p class=\"form-control-static\">CV-ketel</p>\n" +
			"                </div>\n" +
			"            </div>\n" +
			"            <div class=\"form-group\">\n" +
			"                <label class=\"col-xs-6 control-label\">Laatste rapportagetijd</label>\n" +
			"                <div class=\"col-xs-6\">\n" +
			"                    <p class=\"form-control-static\">\n" +
			"  2015-11-18 00:07:48</p>\n" +
			"                </div>\n" +
			"            </div>\n" +
			"            <div class=\"form-group\">\n" +
			"                <label class=\"col-xs-6 control-label\">Branduren</label>\n" +
			"                <div class=\"col-xs-6\">\n" +
			"                    <p class=\"form-control-static\">34,95</p>\n" +
			"                </div>\n" +
			"            </div>\n" +
			"            <div class=\"form-group\">\n" +
			"                <label class=\"col-xs-6 control-label\">Brander status</label>\n" +
			"                <div class=\"col-xs-6\">\n" +
			"                        <p class=\"form-control-static\">Aan</p>\n" +
			"                </div>\n" +
			"            </div>";

		Object actual = HTMLUtils.getValueByLabel(html, String.class, "Apparaat alias");
		assertEquals("CV-ketel", actual);

		actual = HTMLUtils.getValueByLabel(html, String.class, "apparaat Alias");
		assertEquals("CV-ketel", actual);

		actual = HTMLUtils.getValueByLabel(html, String.class, "Device Alias", "apparaat alias");
		assertEquals("CV-ketel", actual);

		actual = HTMLUtils.getValueByLabel(html, String.class, "Laatste rapportagetijd");
		assertEquals("2015-11-18 00:07:48", actual);

		actual = HTMLUtils.getValueByLabel(html, BigDecimal.class, "Branduren");
		assertEquals(BigDecimal.class, actual.getClass());
		assertEquals("34.95", actual.toString());

		actual = HTMLUtils.getValueByLabel(html, Boolean.class, "Brander status");
		assertEquals(Boolean.class, actual.getClass());
		assertTrue((Boolean) actual);
	}

	@Test
	public void testGetRequestVerificationToken() throws Exception {
		final String html = "<div id=\"content\" class=\"col-xs-offset-0 col-xs-12 col-sm-offset-1 col-sm-6\">\n" +
			"<form action=\"/Account/Login\" autocomplete=\"off\" class=\"form-horizontal\" method=\"post\">" +
			"<input name=\"__RequestVerificationToken\" type=\"hidden\" value=\"lFVlMZlt2-YJKAwZWS_K_p3gsQWjZOvBNBZ3lM8io_nFGFL0oRsj4YwQUdqGfyrEqGwEUPmm0FgKH1lPWdk257tuTWQ1\" />    <div class=\"login-container center-block\">\n" +
			"        <fieldset>\n" +
			"            <div class=\"form-group\">\n" +
			"                <input class=\"form-control input-lg text-center\" id=\"Email\" name=\"Email\" placeholder=\"Email\" type=\"email\" value=\"\" />\n";

		final String actual = HTMLUtils.extractRequestVerificationTokenFromHtml(html);
		assertEquals("lFVlMZlt2-YJKAwZWS_K_p3gsQWjZOvBNBZ3lM8io_nFGFL0oRsj4YwQUdqGfyrEqGwEUPmm0FgKH1lPWdk257tuTWQ1", actual);
	}

	@Test
	public void testGetDeviceId() throws Exception {
		final String html = "<tr onclick=\"javascript:changeDeviceAndRedirect('/Home/Index/{0}','6808-1401-3109_15-30-001-555');\">";

		final String actual = HTMLUtils.extractDeviceIdFromHtml(html);
		assertEquals("6808-1401-3109_15-30-001-555", actual);
	}
}

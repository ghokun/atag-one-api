package org.juurlink.atagone;

import lombok.NonNull;
import lombok.extern.java.Log;
import lombok.val;
import org.juurlink.atagone.domain.Configuration;
import org.juurlink.atagone.domain.PortalCredentials;
import org.juurlink.atagone.utils.HTMLUtils;
import org.juurlink.atagone.utils.JSONUtils;
import org.juurlink.atagone.utils.NetworkUtils;
import org.juurlink.atagone.utils.NumberUtils;
import org.juurlink.atagone.utils.StringUtils;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Connect to ATAG One thermostat through ATAG One (internet) portal.
 */
@Log
public class AtagOneRemoteConnector implements AtagOneConnectorInterface {

    private static final String THERMOSTAT_NAME = "ATAG One";

    private static final String URL_LOGIN = "https://portal.atag-one.com/Account/Login";
    private static final String URL_DEVICE_HOME = "https://portal.atag-one.com/Home/Index/{0}";
    private static final String URL_DIAGNOSTICS = "https://portal.atag-one.com/Device/LatestReport";
    private static final String URL_UPDATE_DEVICE_CONTROL = "https://portal.atag-one.com/Home/UpdateDeviceControl/?deviceId={0}";
    private static final String URL_DEVICE_SET_SETPOINT = "https://portal.atag-one.com/Home/DeviceSetSetpoint";

    private final PortalCredentials portalCredentials;

    @Nullable
    private final String versionString;

    /**
     * ATAG One device ID.
     */
    @Nullable
    private String selectedDeviceId;

    /**
     * Construct ATAG One connector to remote device.
     */
    public AtagOneRemoteConnector(@Nonnull @NonNull final Configuration configuration) {

        log.fine("Instantiate " + AtagOneApp.THERMOSTAT_NAME + " remote connector");

        versionString = configuration.getVersion() != null ? configuration.getVersion().toString() : null;

        portalCredentials = PortalCredentials.builder()
            .emailAddress(configuration.getEmail())
            .password(configuration.getPassword())
            .build();
    }

    /**
     * Login ATAG ONE portal and select first Device found.
     *
     * @throws IOException in case connecting to remote device failed
     * @throws IllegalStateException in case connecting succeeded but no device is found
     */
    public void login() throws IOException, IllegalStateException {

        log.fine("Login at " + THERMOSTAT_NAME + " portal.");
        if (StringUtils.isBlank(portalCredentials.getEmailAddress()) || StringUtils.isBlank(portalCredentials.getPassword())) {
            throw new IllegalStateException("Both 'emailAddress' and 'password' are required.");
        }

        log.fine("POST authentication data: " + URL_LOGIN);

        // We need a session (cookie) and a verification token, get them first.
        val requestVerificationToken = getRequestVerificationToken(URL_LOGIN);

        val params = new LinkedHashMap<String, String>();
        params.put("__RequestVerificationToken", requestVerificationToken);
        params.put("Email", portalCredentials.getEmailAddress());
        params.put("Password", portalCredentials.getPassword());
        params.put("RememberMe", "false");

        val html = NetworkUtils.getPostPageContent(URL_LOGIN, params, versionString);
        selectedDeviceId = HTMLUtils.extractDeviceId(html);

        if (StringUtils.isBlank(selectedDeviceId)) {
            throw new IllegalStateException("No Device ID found, cannot continue.");
        }
    }

    /**
     * Get all diagnostics for selected device.
     *
     * @return Map of diagnostic info
     * @throws IOException              in case of connection error
     * @throws IllegalArgumentException when no device selected
     */
    @Nonnull
    public Map<String, Object> getDiagnostics() throws IOException, IllegalArgumentException {

        if (StringUtils.isBlank(selectedDeviceId)) {
            throw new IllegalArgumentException("No Device selected, cannot get diagnostics.");
        }

        val diagnosticsUrl = URL_DIAGNOSTICS + "?deviceId=" + URLEncoder.encode(selectedDeviceId, ENCODING_UTF_8);
        log.fine("GET diagnostics: URL=" + diagnosticsUrl);

        // HTTP(S) Connect.
        val html = NetworkUtils.getPageContent(diagnosticsUrl, versionString);
        log.fine("GET diagnostics: Response HTML\n" + html);

        // Scrape values from HTML page.
        val values = new LinkedHashMap<String, Object>();
        values.put(VALUE_DEVICE_ID, selectedDeviceId);
        values.put(VALUE_DEVICE_ALIAS, HTMLUtils.getValueByLabel(html, String.class, "Apparaat alias", "Device alias"));
        values.put(VALUE_LATEST_REPORT_TIME, HTMLUtils.getValueByLabel(html, String.class, "Laatste rapportagetijd", "Latest report time"));
        values.put(VALUE_CONNECTED_TO, HTMLUtils.getValueByLabel(html, String.class, "Verbonden met", "Connected to"));
        values.put(VALUE_BURNING_HOURS, HTMLUtils.getValueByLabel(html, BigDecimal.class, "Branduren", "Burning hours"));
        values.put(VALUE_BOILER_HEATING_FOR, HTMLUtils.getValueByLabel(html, String.class, "Ketel in bedrijf voor", "Boiler heating for"));
        values.put(VALUE_FLAME_STATUS, HTMLUtils.getValueByLabel(html, Boolean.class, "Brander status", "Flame status"));
        values.put(VALUE_ROOM_TEMPERATURE, HTMLUtils.getValueByLabel(html, BigDecimal.class, "Kamertemperatuur", "Room temperature"));
        values.put(VALUE_OUTSIDE_TEMPERATURE, HTMLUtils.getValueByLabel(html, BigDecimal.class, "Buitentemperatuur", "Outside temperature"));
        values.put(VALUE_DHW_SETPOINT, HTMLUtils.getValueByLabel(html, BigDecimal.class, "Setpoint warmwater", "DHW setpoint"));
        values.put(VALUE_DHW_WATER_TEMPERATURE, HTMLUtils.getValueByLabel(html, BigDecimal.class, "Warmwatertemperatuur", "DHW water temperature"));
        values.put(VALUE_CH_SETPOINT, HTMLUtils.getValueByLabel(html, BigDecimal.class, "Setpoint cv", "CH setpoint"));
        values.put(VALUE_CH_WATER_TEMPERATURE, HTMLUtils.getValueByLabel(html, BigDecimal.class, "CV-aanvoertemperatuur", "CH water temperature"));
        values.put(VALUE_CH_WATER_PRESSURE, HTMLUtils.getValueByLabel(html, BigDecimal.class, "CV-waterdruk", "CH water pressure"));
        values.put(VALUE_CH_RETURN_TEMPERATURE, HTMLUtils.getValueByLabel(html, BigDecimal.class, "CV retourtemperatuur", "CH return temperature"));

        // We have to do an extra call to get the target temperature.
        // {"isHeating":false,"targetTemp":"17.0","currentTemp":"16.9","vacationPlanned":false,"currentMode":"manual"}
        val deviceControlUrl = URL_UPDATE_DEVICE_CONTROL.replace("{0}", URLEncoder.encode(selectedDeviceId, ENCODING_UTF_8));
        log.fine("GET deviceControl: URL=" + deviceControlUrl);

        // HTTP(S) Connect.
        val html2 = NetworkUtils.getPageContent(deviceControlUrl, versionString);
        log.fine("GET deviceControl: Response HTML\n" + html2);

        val targetTemp = JSONUtils.getJSONValueByName(html2, String.class, "targetTemp");
        val targetTempNumber = new BigDecimal(targetTemp != null ? targetTemp : "0");
        values.put(VALUE_TARGET_TEMPERATURE, targetTempNumber);
        values.put(VALUE_CURRENT_MODE, JSONUtils.getJSONValueByName(html2, String.class, "currentMode"));
        values.put(VALUE_VACATION_PLANNED, JSONUtils.getJSONValueByName(html2, Boolean.class, "vacationPlanned"));

        return values;
    }

    /**
     * Set thermostat target temperature.
     */
    @Override
    public BigDecimal setTemperature(@Nonnull @NonNull final BigDecimal targetTemperature)
        throws IOException, IllegalArgumentException, IllegalStateException {

        log.fine("Set target temperature to " + targetTemperature + " degrees celsius");

        // Discard the precision and round by half.
        float roundedTemperature = NumberUtils.roundHalf(targetTemperature.floatValue());
        if (roundedTemperature < AtagOneApp.TEMPERATURE_MIN || roundedTemperature > AtagOneApp.TEMPERATURE_MAX) {
            throw new IllegalArgumentException(
                "Device temperature out of bounds: " + roundedTemperature + ". Needs to be between " + AtagOneApp.TEMPERATURE_MIN +
                    " (inclusive) and " + AtagOneApp.TEMPERATURE_MAX + " (inclusive)");
        }
        if (StringUtils.isBlank(selectedDeviceId)) {
            throw new IllegalArgumentException("No Device selected, cannot get diagnostics.");
        }

        // Get updated request verification token first.
        val requestVerificationToken = getRequestVerificationToken(URL_DEVICE_HOME);

        // https://portal.atag-one.com/Home/DeviceSetSetpoint/6808-1401-3109_15-30-001-544?temperature=18.5
        val newUrl = URL_DEVICE_SET_SETPOINT + "/" + selectedDeviceId + "?temperature=" + roundedTemperature;
        log.fine("POST setDeviceSetPoint: " + newUrl);

        val params = new HashMap<String, String>();
        params.put("__RequestVerificationToken", requestVerificationToken);

        // Response contains current temperature.
        // {\"ch_control_mode\":0,\"temp_influenced\":false,\"room_temp\":18.0,\"ch_mode_temp\":18.2,\"is_heating\":true,\"vacationPlanned\":false,\"temp_increment\":null,\"round_half\":false,\"schedule_base_temp\":null,\"outside_temp\":null}
        val html = NetworkUtils.getPostPageContent(newUrl, params, versionString);
        val roomTemperature = JSONUtils.getJSONValueByName(html, BigDecimal.class, JSON_ROOM_TEMP);
        if (roomTemperature != null) {
            // Ok.
            return roomTemperature;
        }

        throw new IllegalStateException("Cannot read current room temperature.");
    }

    @Override
    public String dump() {
        throw new IllegalStateException("Dump not available in remote operation.");
    }

    /**
     * Open device home page and return requests verification token.          ;
     *
     * @param url URL to connect to
     * @return request verification token
     * @throws IOException           When error connecting to ATAG ONE portal
     * @throws IllegalStateException When session cannot be started
     */
    protected String getRequestVerificationToken(@Nonnull @NonNull String url) throws IOException, IllegalStateException {

        log.fine("getRequestVerificationToken(" + url + ")");

        // HTTP(S) Connect.

        // Try to replace device id, ignore when no replace string available.
        val newUrl = url.replace("{0}", StringUtils.defaultString(selectedDeviceId));
        val html = NetworkUtils.getPageContent(newUrl, versionString);

        // Get request verification.
        val requestVerificationToken = HTMLUtils.extractRequestVerificationToken(html);
        if (!StringUtils.isBlank(requestVerificationToken)) {
            return requestVerificationToken;
        }

        throw new IllegalStateException("No Request Verification Token received.");
    }

}

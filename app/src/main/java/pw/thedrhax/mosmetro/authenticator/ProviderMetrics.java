/**
 * Wi-Fi в метро (pw.thedrhax.mosmetro, Moscow Wi-Fi autologin)
 * Copyright © 2015 Dmitry Karikh <the.dr.hax@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package pw.thedrhax.mosmetro.authenticator;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;

import pw.thedrhax.mosmetro.BuildConfig;
import pw.thedrhax.mosmetro.R;
import pw.thedrhax.mosmetro.activities.SettingsActivity;
import pw.thedrhax.mosmetro.activities.SilentActionActivity;
import pw.thedrhax.mosmetro.httpclient.clients.OkHttp;
import pw.thedrhax.mosmetro.updater.BackendRequest;
import pw.thedrhax.util.Notify;
import pw.thedrhax.util.UUID;
import pw.thedrhax.util.Version;
import pw.thedrhax.util.WifiUtils;

class ProviderMetrics {
    private final Provider p;

    ProviderMetrics(Provider provider) {
        this.p = provider;
    }

    private Long start_ts = null;

    public ProviderMetrics start() {
        start_ts = System.currentTimeMillis();
        return this;
    }

    @SuppressLint("StaticFieldLeak")
    public boolean end(HashMap<String, Object> vars) {
        boolean connected;
        boolean midsession = false;

        switch ((Provider.RESULT) vars.get("result")) {
            case CONNECTED: connected = true; break;
            case ALREADY_CONNECTED: connected = false; break;
            default: return false;
        }

        WifiUtils wifi = new WifiUtils(p.context);

        final HashMap<String, String> params = new HashMap<>();

        params.put("uuid", UUID.get(p.context));
        params.put("version_name", Version.getVersionName());
        params.put("version_code", "" + Version.getVersionCode());
        params.put("build_branch", Version.getBranch());
        params.put("build_number", "" + Version.getBuildNumber());
        params.put("api_level", "" + Build.VERSION.SDK_INT);

        if (vars.containsKey("midsession")) {
            params.put("success", "midsession");
            midsession = true;
        } else {
            params.put("success", connected ? "true" : "false");
        }

        params.put("ssid", wifi.getSSID());
        params.put("provider", p.getName());

        if (start_ts != null) {
            params.put("duration", "" + (System.currentTimeMillis() - start_ts));
        }

        if (vars.containsKey("switch")) {
            params.put("switch", (String) vars.get("switch"));
        }

        if (vars.containsKey("segment")) {
            params.put("segment", (String) vars.get("segment"));
        }

        if (vars.containsKey("branch")) {
            params.put("branch", (String) vars.get("branch"));
        }

        String STATISTICS_URL = p.settings.getString(
                BackendRequest.PREF_BACKEND_URL,
                BuildConfig.API_URL_DEFAULT
        ) + BuildConfig.API_REL_STATISTICS;

        try {
            new OkHttp(p.context).post(STATISTICS_URL, params).execute();
        } catch (IOException ignored) {}

        if (System.currentTimeMillis() - 6*60*60*1000 > p.settings.getLong("pref_worker_timestamp", 0)) {
            new BackendRequest(p.context).run();
        }

        boolean pref_notify_donate = p.settings.getBoolean("pref_notify_donate", true);
        boolean pref_notify_donate_freq = p.settings.getBoolean("pref_notify_donate_freq", false);
        int stat_connections = p.settings.getInt("stat_connections", 0);

        if (pref_notify_donate && connected && !midsession) {
            stat_connections += 1;
            p.settings.edit().putInt("stat_connections", stat_connections).apply();

            if (stat_connections > 0 && stat_connections % (pref_notify_donate_freq ? 100 : 50) == 0) {
                Notify notify = new Notify(p.context)
                        .id(128)
                        .title(String.format(Locale.ENGLISH, p.context.getString(R.string.notification_donate_title), stat_connections))
                        .text(p.context.getString(R.string.notification_donate_text))
                        .icon(R.drawable.ic_notification_message_colored, R.drawable.ic_notification_message)
                        .cancelOnClick(true);

                if (!pref_notify_donate_freq) {
                    notify.addAction(p.context.getString(R.string.remind_less_frequently), PendingIntent.getActivity(
                            p.context, 129,
                            new Intent(p.context, SilentActionActivity.class)
                                    .setAction(SilentActionActivity.ACTION_TOGGLE_DONATE_REMINDER_FREQUENCY),
                            PendingIntent.FLAG_CANCEL_CURRENT
                    ));
                }

                notify.addAction(p.context.getString(R.string.do_not_show), PendingIntent.getActivity(
                        p.context, 128,
                        new Intent(p.context, SilentActionActivity.class)
                                .setAction(SilentActionActivity.ACTION_DISABLE_DONATE_REMINDER),
                        PendingIntent.FLAG_UPDATE_CURRENT
                ));

                notify.onClick(PendingIntent.getActivity(
                        p.context, 130,
                        new Intent(p.context, SettingsActivity.class)
                                .setAction(SettingsActivity.ACTION_DONATE),
                        PendingIntent.FLAG_UPDATE_CURRENT
                ));

                notify.show();
            }
        }

        return false;
    }
}

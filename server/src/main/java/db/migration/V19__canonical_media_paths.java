package db.migration;

import java.sql.Connection;
import java.util.Map;
import java.util.regex.Pattern;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/** Remove only the host prefix of this application's media API, preserving external assets. */
public class V19__canonical_media_paths extends BaseJavaMigration {
  private static final Pattern MEDIA =
      Pattern.compile("https?://[^/\\s\"<>]+(/api/activities/[0-9a-fA-F-]{36}/media/)");

  @Override
  public void migrate(Context context) throws Exception {
    Map<String, String[]> columns =
        Map.of(
            "questions", new String[] {"media_url", "media_urls", "answer_media_urls"},
            "activities", new String[] {"client_hero_image_url", "client_background_image_url"},
            "site_settings", new String[] {"logo_url"},
            "screen_templates", new String[] {"components_json"},
            "screen_devices", new String[] {"display_payload_json"});
    Connection connection = context.getConnection();
    for (var table : columns.entrySet()) {
      for (String column : table.getValue()) {
        try (var select =
                connection.prepareStatement("select id, " + column + " from " + table.getKey());
            var rows = select.executeQuery();
            var update =
                connection.prepareStatement(
                    "update " + table.getKey() + " set " + column + "=? where id=?")) {
          while (rows.next()) {
            String original = rows.getString(2);
            if (original == null) continue;
            String normalized = MEDIA.matcher(original).replaceAll("$1");
            if (original.equals(normalized)) continue;
            update.setString(1, normalized);
            update.setObject(2, rows.getObject(1));
            update.executeUpdate();
          }
        }
      }
    }
  }
}

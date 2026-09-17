package site.infinitescope.glance.fetch;

import site.infinitescope.glance.config.GlanceProperties;

import java.util.List;

public interface SourceFetcher {

    boolean supports(GlanceProperties.Source source);

    List<FetchedItem> fetch(GlanceProperties.Source source);
}

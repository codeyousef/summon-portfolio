package code.yousef.portfolio.ui.foundation

import codes.yousef.summon.annotation.Composable
import codes.yousef.summon.runtime.LocalPlatformRenderer

/**
 * PostHog analytics configuration.
 * Reads API key and host from environment variables at runtime.
 */
object PostHogConfig {
    val apiKey: String? = System.getenv("NEXT_PUBLIC_POSTHOG_KEY")
    val host: String = System.getenv("NEXT_PUBLIC_POSTHOG_HOST") ?: "https://us.i.posthog.com"
    
    val isEnabled: Boolean
        get() = !apiKey.isNullOrBlank()
}

/**
 * Injects PostHog analytics script into the page head.
 * This should be called once per page, typically from PageScaffold.
 */
@Composable
fun PostHogAnalytics() {
    if (!PostHogConfig.isEnabled) return
    
    val renderer = runCatching { LocalPlatformRenderer.current }.getOrNull() ?: return
    
    val apiKey = PostHogConfig.apiKey ?: return
    val host = PostHogConfig.host
    
    // PostHog snippet - loads asynchronously and captures pageviews
    val postHogScript = """
        !function(t,e){var o,n,p,r;e.__SV||(window.posthog=e,e._i=[],e.init=function(i,s,a){function g(t,e){var o=e.split(".");2==o.length&&(t=t[o[0]],e=o[1]),t[e]=function(){t.push([e].concat(Array.prototype.slice.call(arguments,0)))}}(p=t.createElement("script")).type="text/javascript",p.async=!0,p.src=s.api_host+"/static/array.js",(r=t.getElementsByTagName("script")[0]).parentNode.insertBefore(p,r);var u=e;for(void 0!==a?u=e[a]=[]:a="posthog",u.people=u.people||[],u.toString=function(t){var e="posthog";return"posthog"!==a&&(e+="."+a),t||(e+=" (stub)"),e},u.people.toString=function(){return u.toString(1)+".people (stub)"},o="capture identify alias people.set people.set_once set_config register register_once unregister opt_out_capturing has_opted_out_capturing opt_in_capturing reset isFeatureEnabled onFeatureFlags getFeatureFlag getFeatureFlagPayload reloadFeatureFlags group updateEarlyAccessFeatureEnrollment getEarlyAccessFeatures getActiveMatchingSurveys getSurveys onSessionId".split(" "),n=0;n<o.length;n++)g(u,o[n]);e._i.push([i,s,a])},e.__SV=1)}(document,window.posthog||[]);
        posthog.init('$apiKey', {
            api_host: '$host',
            person_profiles: 'identified_only',
            capture_pageview: true,
            capture_pageleave: true,
            autocapture: true
        });
    """.trimIndent()
    
    renderer.renderHeadElements {
        script(
            src = null,              // no external source, inline script
            content = postHogScript, // inline script content
            type = "text/javascript",
            async = false,
            defer = false,
            crossorigin = null
        )
    }
}

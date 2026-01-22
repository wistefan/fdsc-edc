package org.seamware.edc.apisix;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class ProxyRewritePlugin extends ApisixPlugin {

    private String uri;
    private List<String> regexUri;

    public String getUri() {
        return uri;
    }

    public ProxyRewritePlugin setUri(String uri) {
        this.uri = uri;
        return this;
    }

    @JsonProperty("regex_uri")
    public List<String> getRegexUri() {
        return regexUri;
    }

    @JsonProperty("regex_uri")
    public ProxyRewritePlugin setRegexUri(List<String> regexUri) {
        this.regexUri = regexUri;
        return this;
    }

    @JsonIgnore
    @Override
    public String getPluginName() {
        return "proxy-rewrite";
    }
}

package net;

import com.google.gson.annotations.SerializedName;
import lombok.Getter;

import java.util.Map;

@Getter
public class HttpRequestData {
    @SerializedName("ip.version")
    private Integer ipVersion;

    @SerializedName("ip.dst")
    private String dstAddress;

    @SerializedName("tcp.dstport")
    private Integer dstPort;

    @SerializedName("http.request.method")
    private String method;

    @SerializedName("http.request.uri")
    private String uri;

    @SerializedName("http.request.version")
    private String httpVersion;

    private Map<String, String> headers;
}

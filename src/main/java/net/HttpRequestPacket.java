package net;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
public class HttpRequestPacket {
    @Expose
    @SerializedName("ip.version")
    private Integer ipVersion;

    @Expose
    @SerializedName("ip.dst")
    private String dstAddress;

    @Expose
    @SerializedName("tcp.dstport")
    private Integer dstPort;

    @Expose
    @SerializedName("http.request.method")
    private String method;

    @Expose
    @SerializedName("http.request.uri")
    private String uri;

    @Expose
    @SerializedName("http.request.version")
    private String httpVersion;

    @Expose
    @SerializedName("http.request.full_uri")
    private String fullUri;

//    @SerializedName("http.file_data") TODO, string, binary test.
//    private byte[] payload;

    @Setter
    private Map<String, String> headers;
}

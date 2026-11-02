package local.grworld.studio;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import local.grworld.studio.spi.CameraTransport;
import org.json.JSONObject;

final class DesktopCamera implements CameraTransport {
  record Adapter(String name, Inet4Address address, int index) {
    public String toString() {
      return name + " · " + address.getHostAddress();
    }

    String key() {
      return index + "/" + address.getHostAddress();
    }
  }

  volatile Adapter selected;

  static List<Adapter> adapters() throws SocketException {
    List<Adapter> out = new ArrayList<>();
    for (NetworkInterface n : Collections.list(NetworkInterface.getNetworkInterfaces()))
      if (n.isUp() && !n.isLoopback())
        for (InetAddress ip : Collections.list(n.getInetAddresses()))
          if (ip instanceof Inet4Address v4 && !ip.isLinkLocalAddress())
            out.add(new Adapter(n.getDisplayName(), v4, n.getIndex()));
    return out;
  }

  Adapter adapter() throws Exception {
    List<Adapter> all = adapters();
    if (selected != null) {
      for (Adapter a : all) if (a.key().equals(selected.key())) return a;
      throw new IOException("已选择的网络连接已变化，请重新检测");
    }
    List<Adapter> likely =
        all.stream().filter(a -> a.address().getHostAddress().startsWith("192.168.0.")).toList();
    if (likely.size() != 1)
      throw new IOException(
          likely.isEmpty() ? "请先让电脑连接相机 Wi-Fi，再检测连接。" : "有多个可能的相机网络，请在设置中选择相机所在网卡。");
    return likely.get(0);
  }

  JSONObject request(Adapter adapter, String method, String path, byte[] jpeg) throws Exception {
    HttpClient client =
        HttpClient.newBuilder()
            .localAddress(adapter.address())
            .proxy(HttpClient.Builder.NO_PROXY)
            .connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER)
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    try (client) {
      HttpRequest.Builder request =
          HttpRequest.newBuilder(URI.create("http://192.168.0.1" + path))
              .timeout(Duration.ofSeconds(jpeg == null ? 3 : 30));
      if (jpeg == null) request.GET();
      else
        request
            .header("Content-Type", "image/jpeg")
            .method(method, HttpRequest.BodyPublishers.ofByteArray(jpeg));
      HttpResponse<InputStream> response =
          client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
      byte[] body = DesktopResources.limited(response.body(), 256 * 1024);
      JSONObject json = new JSONObject(new String(body, StandardCharsets.UTF_8));
      if (!EndScreenPolicy.accepted(response.statusCode(), json.optInt("errCode", -1)))
        throw new IOException(
            "相机未接受操作：HTTP " + response.statusCode() + " / " + json.optInt("errCode", -1));
      return json;
    }
  }

  public Identity identify() throws Exception {
    Adapter n = adapter();
    long start = System.nanoTime();
    JSONObject p = request(n, "GET", "/v1/props", null);
    if (!n.key().equals(adapter().key())) throw new IOException("检测期间网络变化");
    return new Identity(
        p.optString("model"),
        p.optString("firmwareVersion"),
        n.key(),
        (System.nanoTime() - start) / 1_000_000);
  }

  public void upload(byte[] jpeg, String expected) throws Exception {
    EndScreenPolicy.image(jpeg);
    Adapter n = adapter();
    if (!n.key().equals(expected)) throw new IOException("相机网络已变化，未上传");
    request(n, "PUT", EndScreenPolicy.path(jpeg), jpeg);
  }
}

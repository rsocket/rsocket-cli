package io.rsocket.cli.http2;

import static java.util.Optional.of;

import io.rsocket.transport.ClientTransport;
import io.rsocket.uri.UriHandler;
import org.eclipse.jetty.http.HttpScheme;

import java.net.URI;
import java.util.Optional;

public class Http2UriHandler implements UriHandler {
  @Override
  public Optional<ClientTransport> buildClient(URI uri) {
    if (HttpScheme.HTTPS.is(uri.getScheme()) || HttpScheme.HTTP.is(uri.getScheme())) {
      return of(new Http2ClientTransport(uri));
    }

    return UriHandler.super.buildClient(uri);
  }
}

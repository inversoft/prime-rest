/*
 * Copyright (c) 2016, Inversoft Inc., All Rights Reserved
 */
package com.inversoft.rest;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.inversoft.net.ssl.SSLTools;

/**
 * RESTful WebService call builder. This provides the ability to call RESTful WebServices using a builder pattern to
 * set up all the necessary request information and parse the response.
 *
 * @author Brian Pontarelli
 */
public class RESTClient<RS, ERS> {
  private static final Logger logger = LoggerFactory.getLogger(RESTClient.class);

  public final Map<String, String> headers = new HashMap<>();

  public final Map<String, List<Object>> parameters = new LinkedHashMap<>();

  public final StringBuilder url = new StringBuilder();

  public BodyHandler bodyHandler;

  public String certificate;

  public int connectTimeout = 2000;

  public ResponseHandler<ERS> errorResponseFunction;

  public String key;

  public HTTPMethod method;

  public int readTimeout = 2000;

  public ResponseHandler<RS> successResponseFunction;

  protected RESTClient() {
  }

  public RESTClient<RS, ERS> authorization(String key) {
    this.headers.put("Authorization", key);
    return this;
  }

  public RESTClient<RS, ERS> basicAuthorization(String username, String password) {
    if (username != null && password != null) {
      String credentials = username + ":" + password;
      Base64.Encoder encoder = Base64.getEncoder();
      String encoded = encoder.encodeToString(credentials.getBytes());
      this.headers.put("Authorization", "Basic " + encoded);
    }
    return this;
  }

  public RESTClient<RS, ERS> bodyHandler(BodyHandler bodyHandler) {
    this.bodyHandler = bodyHandler;
    return this;
  }

  public RESTClient<RS, ERS> certificate(String certificate) {
    this.certificate = certificate;
    return this;
  }

  public RESTClient<RS, ERS> connectTimeout(int connectTimeout) {
    this.connectTimeout = connectTimeout;
    return this;
  }

  public RESTClient<RS, ERS> delete() {
    this.method = HTTPMethod.DELETE;
    return this;
  }

  public RESTClient<RS, ERS> errorResponseHandler(ResponseHandler<ERS> errorResponseFunction) {
    this.errorResponseFunction = errorResponseFunction;
    return this;
  }

  public RESTClient<RS, ERS> get() {
    this.method = HTTPMethod.GET;
    return this;
  }

  public ClientResponse<RS, ERS> go() {
    if (url.length() == 0) {
      throw new IllegalStateException("You must specify a URL");
    }

    Objects.requireNonNull(method, "You must specify a HTTP method");

    ClientResponse<RS, ERS> response = new ClientResponse<>();
    HttpURLConnection huc;
    try {
      if (parameters.size() > 0) {
        if (url.indexOf("?") == -1) {
          url.append("?");
        }

        for (Iterator<Entry<String, List<Object>>> i = parameters.entrySet().iterator(); i.hasNext(); ) {
          Entry<String, List<Object>> entry = i.next();

          for (Iterator<Object> j = entry.getValue().iterator(); j.hasNext(); ) {
            Object value = j.next();
            url.append(URLEncoder.encode(entry.getKey(), "UTF-8")).append("=").append(URLEncoder.encode(value.toString(), "UTF-8"));
            if (j.hasNext()) {
              url.append("&");
            }
          }

          if (i.hasNext()) {
            url.append("&");
          }
        }
      }

      URL urlObject = new URL(url.toString());
      huc = (HttpURLConnection) urlObject.openConnection();
      if (urlObject.getProtocol().toLowerCase().equals("https") && certificate != null) {
        HttpsURLConnection hsuc = (HttpsURLConnection) huc;
        if (key != null) {
          hsuc.setSSLSocketFactory(SSLTools.getSSLServerContext(certificate, key).getSocketFactory());
        } else {
          hsuc.setSSLSocketFactory(SSLTools.getSSLSocketFactory(certificate));
        }
      }

      huc.setDoOutput(bodyHandler != null);
      huc.setConnectTimeout(connectTimeout);
      huc.setReadTimeout(readTimeout);
      huc.setRequestMethod(method.toString());

      if (headers.size() > 0) {
        headers.forEach(huc::addRequestProperty);
      }

      if (bodyHandler != null) {
        bodyHandler.setHeaders(huc);
      }

      huc.connect();

      if (bodyHandler != null) {
        try (OutputStream os = huc.getOutputStream()) {
          bodyHandler.accept(os);
          os.flush();
        }
      }
    } catch (Exception e) {
      logger.debug("Error calling REST WebService at [" + url + "]", e);
      response.exception = e;
      return response;
    }

    int status;
    try {
      status = huc.getResponseCode();
    } catch (Exception e) {
      logger.debug("Error calling REST WebService at [" + url + "]", e);
      response.exception = e;
      return response;
    }

    response.status = status;

    if (status < 200 || status > 299) {
      if (errorResponseFunction == null) {
        return response;
      }

      try (InputStream is = huc.getErrorStream()) {
        response.errorResponse = errorResponseFunction.apply(is);
      } catch (Exception e) {
        logger.debug("Error calling REST WebService at [" + url + "]", e);
        response.exception = e;
        return response;
      }
    } else {
      if (successResponseFunction == null) {
        return response;
      }

      try (InputStream is = huc.getInputStream()) {
        response.successResponse = successResponseFunction.apply(is);
      } catch (Exception e) {
        logger.debug("Error calling REST WebService at [" + url + "]", e);
        response.exception = e;
        return response;
      }
    }

    return response;
  }

  public RESTClient<RS, ERS> header(String name, String value) {
    this.headers.put(name, value);
    return this;
  }

  public RESTClient<RS, ERS> headers(Map<String, String> headers) {
    this.headers.putAll(headers);
    return this;
  }

  public RESTClient<RS, ERS> key(String key) {
    this.key = key;
    return this;
  }

  public RESTClient<RS, ERS> post() {
    this.method = HTTPMethod.POST;
    return this;
  }

  public RESTClient<RS, ERS> put() {
    this.method = HTTPMethod.PUT;
    return this;
  }

  public RESTClient<RS, ERS> readTimeout(int readTimeout) {
    this.readTimeout = readTimeout;
    return this;
  }

  public RESTClient<RS, ERS> successResponseHandler(ResponseHandler<RS> successResponseFunction) {
    this.successResponseFunction = successResponseFunction;
    return this;
  }

  public RESTClient<RS, ERS> uri(String uri) {
    if (url.length() == 0) {
      return this;
    }

    if (url.charAt(url.length() - 1) == '/' && uri.startsWith("/")) {
      url.append(uri.substring(1));
    } else if (url.charAt(url.length() - 1) != '/' && !uri.startsWith("/")) {
      url.append("/").append(uri);
    } else {
      url.append(uri);
    }

    return this;
  }

  public RESTClient<RS, ERS> url(String url) {
    this.url.delete(0, this.url.length());
    this.url.append(url);
    return this;
  }

  /**
   * Add a URL parameter as a key value pair.
   *
   * @param name  The URL parameter name.
   * @param value The url parameter value. The <code>.toString()</code> method will be used to
   *              get the <code>String</code> used in the URL parameter. If the object type is a
   *              {@link Collection} a key value pair will be added for each value in the collection.
   *              {@link ZonedDateTime} will also be handled uniquely in that the <code>long</code> will
   *              be used to set in the request using <code>ZonedDateTime.toInstant().toEpochMilli()</code>
   * @return This.
   */
  public RESTClient<RS, ERS> urlParameter(String name, Object value) {
    if (value == null) {
      return this;
    }

    List<Object> values = this.parameters.get(name);
    if (values == null) {
      values = new ArrayList<>();
      this.parameters.put(name, values);
    }

    if (value instanceof ZonedDateTime) {
      values.add(((ZonedDateTime) value).toInstant().toEpochMilli());
    } else if (value instanceof Collection) {
      values.addAll((Collection) value);
    } else {
      values.add(value);
    }
    return this;
  }

  /**
   * Append a url path segment. <p>
   * For Example: <pre>
   *     .url("http://www.foo.com")
   *     .urlSegment("bar")
   *   </pre>
   * This will result in a url of <code>http://www.foo.com/bar</code>
   *
   * @param value The url path segment. A null value will be ignored.
   * @return This.
   */
  public RESTClient<RS, ERS> urlSegment(Object value) {
    if (value != null) {
      if (url.charAt(url.length() - 1) != '/') {
        url.append('/');
      }
      url.append(value.toString());
    }
    return this;
  }

  public enum HTTPMethod {
    GET,
    POST,
    PUT,
    DELETE
  }

  /**
   * Body handler that manages sending the bytes of the HTTP request body to the HttpURLConnection. This also is able to
   * manage any HTTP headers that are associated with the body such as Content-Type and Content-Length.
   */
  public interface BodyHandler {
    /**
     * Accepts the OutputStream and writes the bytes of the HTTP request body to it.
     *
     * @param os The OutputStream to write the body to.
     * @throws IOException If the write failed.
     */
    void accept(OutputStream os) throws IOException;

    /**
     * Sets any headers for the HTTP body that will be written.
     *
     * @param huc The HttpURLConnection to set headers into.
     */
    void setHeaders(HttpURLConnection huc);
  }

  /**
   * Handles responses from the HTTP server.
   *
   * @param <T> The type that is returned from the handler.
   */
  public interface ResponseHandler<T> {
    /**
     * Handles the InputStream that is the HTTP response and reads it in and converts it to a value.
     *
     * @param is The InputStream to read from.
     * @return The value.
     * @throws IOException If the read failed.
     */
    T apply(InputStream is) throws IOException;
  }
}
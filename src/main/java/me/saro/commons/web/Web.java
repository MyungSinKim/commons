package me.saro.commons.web;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import me.saro.commons.Converter;
import me.saro.commons.Files;
import me.saro.commons.JsonReader;
import me.saro.commons.function.ThrowableConsumer;
import me.saro.commons.function.ThrowableFunction;

/**
 * Web Client
 * @author		PARK Yong Seo
 * @since		0.1
 */
public class Web {

    // url
    final String url;

    // method
    final String method;

    // chaset
    String requestCharset = "UTF-8";
    String responseCharset = "UTF-8";

    // url parameter
    StringBuilder urlParameter = new StringBuilder(100);

    // request header
    Map<String, String> header = new HashMap<>();

    // request body
    ByteArrayOutputStream body = new ByteArrayOutputStream(8192);

    // ignore certificate
    boolean ignoreCertificate = false;
    
    // connectTimeout
    int connectTimeout;
    
    // readTimeout
    int readTimeout;

    /**
     * private constructor
     * @param url
     * @param method
     */
    private Web(String url, String method) {
        int point;
        if ((point = url.indexOf('?')) > -1) {
            if ((point) < url.length()) {
                urlParameter.append(url.substring(point));
            }
            url = url.substring(0, point);
        } else {
            urlParameter.append('?');
        }
        this.url = url;
        this.method = method;
    }
    
    /**
     * Connect Timeout
     * @param connectTimeout
     * @return
     */
    public Web setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
        return this;
    }
    
    /**
     * Read Timeout
     * @param readTimeout
     * @return
     */
    public Web setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
        return this;
    }

    /**
     * create get method Web
     * @param url
     * @return
     */
    public static Web get(String url) {
        return new Web(url, "GET");
    }

    /**
     * create post method Web
     * @param url
     * @return
     */
    public static Web post(String url) {
        return new Web(url, "POST");
    }

    /**
     * create put method Web
     * @param url
     * @return
     */
    public static Web put(String url) {
        return new Web(url, "PUT");
    }

    /**
     * create patch method Web
     * @param url
     * @return
     */
    public static Web patch(String url) {
        return new Web(url, "PATCH");
    }

    /**
     * create delete method Web
     * @param url
     * @return
     */
    public static Web delete(String url) {
        return new Web(url, "DELETE");
    }

    /**
     * create custom method Web
     * @param url
     * @return
     */
    public static Web custom(String url, String method) {
        return new Web(url, method);
    }

    /**
     * set request Charset
     * @param charset
     * @return
     */
    public Web setRequestCharset(String charset) {
        this.requestCharset = charset;
        return this;
    }

    /**
     * set response charset
     * @param charset
     * @return
     */
    public Web setResponseCharset(String charset) {
        this.responseCharset = charset;
        return this;
    }

    /**
     * ignore https certificate
     * <br>
     * this method not recommend
     * <br>
     * ignore certificate is defenseless the MITM(man-in-the-middle attack)
     * @param ignoreCertificate
     * @return
     */
    public Web setIgnoreCertificate(boolean ignoreCertificate) {
        this.ignoreCertificate = ignoreCertificate;
        return this;
    }

    /**
     * add url parameter
     * <br>
     * always append url parameter even post method
     * <br>
     * is not body write
     * @param name
     * @param value
     * @return
     */
    public Web addUrlParameter(String name, String value) {
        try {
            if (urlParameter.length() > 1) {
                urlParameter.append('&');
            }
            urlParameter.append(name).append('=').append(URLEncoder.encode(value, requestCharset));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    /**
     * set header
     * @param name
     * @param value
     * @return
     */
    public Web setHeader(String name, String value) {
        header.put(name, value);
        return this;
    }

    /**
     * set header ContentType
     * @param value
     * @return
     */
    public Web setContentType(String value) {
        return setHeader("Content-Type", value);
    }

    /**
     * write body binary
     * @param bytes
     * @return
     */
    public Web writeBody(byte[] bytes) {
        try {
            body.write(bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    /**
     * write Body text
     * @param text
     * @return
     */
    public Web writeBody(String text) {
        try {
            return writeBody(text.getBytes(requestCharset));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * write json class
     * <br>
     * use jackson lib
     * @param toJsonObject
     * @return
     * @see
     * com.fasterxml.jackson.databind.ObjectMapper
     */
    public Web writeJsonByClass(Object toJsonObject) {
        return writeBody(Converter.toJson(toJsonObject));
    }

    /**
     * writeBodyParameter
     * <br>
     * <b>WARNING : </b> is not json type
     * <br>
     * <br>
     * web
     * <br>
     * 	.writeBodyParameter("aa", "11")
     * <br>
     * .writeBodyParameter("bb", "22");
     * <br>
     * <b>equals</b>
     * <br>
     * aa=11&amp;bb=22
     * @param name
     * @param value
     * @return
     */
    public Web writeBodyParameter(String name, String value) {
        if (body.size() > 0) {
            body.write('&');
        }
        try {
            body.write(URLEncoder.encode(name, requestCharset).getBytes());
            body.write('=');
            body.write(URLEncoder.encode(value, requestCharset).getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    /**
     * to Custom result
     * @param result
     * @param function
     * @return
     */
    public <R> WebResult<R> toCustom(WebResult<R> result, ThrowableFunction<InputStream, R> function) {
        HttpURLConnection connection = null;

        try {
            connection = (HttpURLConnection)(new URL(urlParameter.length() > 1 ? (url + urlParameter.toString()) : url )).openConnection();

            if (ignoreCertificate) {
                WebIgnoreCertificate.ignoreCertificate(connection);
            }
            
            if (connectTimeout > 0) {
                connection.setConnectTimeout(connectTimeout);
            }
            if (readTimeout > 0) {
                connection.setReadTimeout(readTimeout);
            }

            header.forEach(connection::setRequestProperty);

            if (body.size() > 0) {
                connection.setDoOutput(true);
                try (OutputStream os = connection.getOutputStream()) {
                    os.write(body.toByteArray());
                    os.flush();
                }
            }

            result.setStatus(connection.getResponseCode());
            result.setHeaders(connection.getHeaderFields());

            InputStream commonInputStream;
            try {
                commonInputStream = connection.getInputStream();
            } catch (IOException ie) {
                commonInputStream = connection.getErrorStream();
            }

            try (InputStream is = commonInputStream) {
                result.setBody(function.apply(is));
            }
        } catch (Exception e) {
            result.setException(e);
        }

        return result;
    }

    /**
     * to Custom result
     * @param function
     * @return
     */
    public <R> WebResult<R> toCustom(ThrowableFunction<InputStream, R> function) {
        return toCustom(new WebResult<R>(), function);
    }

    /**
     * to Map result by JsonObject
     * @return
     */
    public WebResult<Map<String, Object>> toMapByJsonObject() {
        return toJsonTypeReference(new TypeReference<Map<String, Object>>(){});
    }

    /**
     * to Map List result by JsonArray
     * @return
     */
    public WebResult<List<Map<String, Object>>> toMapListByJsonArray() {
        return toJsonTypeReference(new TypeReference<List<Map<String, Object>>>(){});
    }

    /**
     * to JsonReader
     * @return
     */
    public WebResult<JsonReader> toJsonReader() {
        return toCustom(is -> JsonReader.create(Converter.toStringNotClose(is, responseCharset)));
    }

    /**
     * to Json result by TypeReference
     * @param typeReference
     * @return
     */
    public <T> WebResult<T> toJsonTypeReference(TypeReference<T> typeReference) {
        return toCustom(is -> new ObjectMapper().readValue(is, typeReference));
    }

    /**
     * to text result
     * @return
     */
    public WebResult<String> toPlainText() {
        return toCustom(is -> Converter.toStringNotClose(is, responseCharset));
    }

    /**
     * save file and return WebResult
     * @return WebResult[WebResult]
     */
    public WebResult<File> saveFile(File file, boolean overwrite) {
        return toCustom(is -> {
            Files.createFile(file, overwrite, is);
            return file;
        });
    }

    /**
     * readRawResultStream
     * @param reader
     * @return it has Body
     */
    public WebResult<String> readRawResultStream(ThrowableConsumer<InputStream> reader) {
        return toCustom(is -> {
            reader.accept(is);
            return "OK";
        });
    }
}

package uk.org.taverna.httpclient.jarcache;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.Header;
import org.apache.http.HttpVersion;
import org.apache.http.client.cache.HeaderConstants;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.HttpCacheStorage;
import org.apache.http.client.cache.HttpCacheUpdateCallback;
import org.apache.http.client.cache.HttpCacheUpdateException;
import org.apache.http.client.cache.Resource;
import org.apache.http.impl.client.cache.CacheConfig;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.protocol.HTTP;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JarCacheStorage implements HttpCacheStorage {

	private final Log log = LogFactory.getLog(getClass());
	
	private CacheConfig cacheConfig = new CacheConfig();
	private ClassLoader classLoader;

	public JarCacheStorage() {
		this(Thread.currentThread().getContextClassLoader());
	}

	public JarCacheStorage(ClassLoader classLoader) {
		this.classLoader = classLoader;
		cacheConfig.setMaxObjectSize(0);
		cacheConfig.setMaxCacheEntries(0);
		cacheConfig.setMaxUpdateRetries(0);
		cacheConfig.getMaxCacheEntries();
	}

	@Override
	public void putEntry(String key, HttpCacheEntry entry) throws IOException {
		// ignored

	}

	ObjectMapper mapper = new ObjectMapper();

	@Override
	public HttpCacheEntry getEntry(String key) throws IOException {
		log.trace("Requesting " + key);
		URI requestedUri;
		try {
			requestedUri = new URI(key);
		} catch (URISyntaxException e) {
			return null;
		}
		if ((requestedUri.getScheme().equals("http") && requestedUri.getPort() == 80)
				|| (requestedUri.getScheme().equals("https") && requestedUri
						.getPort() == 443)) {
			// Strip away default http ports
			try {
				requestedUri = new URI(requestedUri.getScheme(),
						requestedUri.getHost(), requestedUri.getPath(),
						requestedUri.getFragment());
			} catch (URISyntaxException e) {
			}
		}

		Enumeration<URL> jarcaches = classLoader.getResources("jarcache.json");
		while (jarcaches.hasMoreElements()) {
			URL url = jarcaches.nextElement();
			JsonNode tree = mapper.readTree(url);
			// TODO: Cache tree per URL
			for (JsonNode node : tree) {
				URI uri = URI.create(node.get("Content-Location").asText());
				if (uri.equals(requestedUri)) {
					final URL classpath = new URL(url, node.get("X-Classpath")
							.asText());
					log.debug("Cache hit for " + requestedUri);
					log.trace(node);

					List<Header> responseHeaders = new ArrayList<Header>();
					if (!node.has(HTTP.DATE_HEADER)) {
						responseHeaders.add(new BasicHeader(HTTP.DATE_HEADER,
								DateUtils.formatDate(new Date())));
					}
					if (!node.has(HeaderConstants.CACHE_CONTROL)) {
						responseHeaders.add(new BasicHeader(
								HeaderConstants.CACHE_CONTROL,
								HeaderConstants.CACHE_CONTROL_MAX_AGE + "="
										+ Integer.MAX_VALUE));
					}
					Resource resource = new JarCacheResource(classpath);
					Iterator<String> fieldNames = node.fieldNames();
					while (fieldNames.hasNext()) {
						String headerName = fieldNames.next();
						JsonNode header = node.get(headerName);
						// TODO: Support multiple headers with []
						responseHeaders.add(new BasicHeader(headerName, header
								.asText()));
					}

					return new HttpCacheEntry(
							new Date(),
							new Date(),
							new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK"),
							responseHeaders.toArray(new Header[0]), resource);

				}
			}
		}
		return null;
	}

	@Override
	public void removeEntry(String key) throws IOException {
		// Ignored
	}

	@Override
	public void updateEntry(String key, HttpCacheUpdateCallback callback)
			throws IOException, HttpCacheUpdateException {
		// ignored
	}

	public CacheConfig getCacheConfig() {
		return cacheConfig;
	}

}

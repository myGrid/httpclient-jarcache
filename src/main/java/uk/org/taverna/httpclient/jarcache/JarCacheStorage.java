package uk.org.taverna.httpclient.jarcache;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

import com.fasterxml.jackson.core.JsonProcessingException;
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
		if (classLoader == null) {
			classLoader = getClass().getClassLoader();
		}
		if (classLoader == null) {
			classLoader = ClassLoader.getSystemClassLoader();
		}
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
			
			JsonNode tree = getJarCache(url);
			// TODO: Cache tree per URL
			for (JsonNode node : tree) {
				URI uri = URI.create(node.get("Content-Location").asText());
				if (uri.equals(requestedUri)) {
					return cacheEntry(requestedUri, url, node);

				}
			}
		}
		return null;
	}

	/** Map from uri of jarcache.json (e.g. jar://blab.jar!jarcache.json)
	* to a SoftReference to its content as JsonNode.
	* 
	* @see #getJarCache(URL)
	*/
	protected Map<URI, SoftReference<JsonNode>> jarCaches = new ConcurrentHashMap(new HashMap<URI, SoftReference<JsonNode>>());
	
	protected JsonNode getJarCache(URL url) throws IOException,
			JsonProcessingException {
		
		URI uri;
		try {
			uri = url.toURI();
		} catch (URISyntaxException e) {
			throw new IllegalArgumentException("Invalid jarCache URI " + url, e);
		}

		// Check if we have one from before - we'll use SoftReference so that
		// 
		SoftReference<JsonNode> jarCacheRef = jarCaches.get(uri);
		if (jarCacheRef != null) {
			JsonNode jarCache = jarCacheRef.get();
			if (jarCache != null) {
				return jarCache;
			} else {
				jarCaches.remove(uri);
			}
		}
		
		JsonNode tree = mapper.readTree(url);
		jarCaches.put(uri, new SoftReference<JsonNode>(tree));
		return tree;
	}

	protected HttpCacheEntry cacheEntry(URI requestedUri, URL baseURL, JsonNode cacheNode)
			throws MalformedURLException, IOException {
		final URL classpath = new URL(baseURL, cacheNode.get("X-Classpath")
				.asText());
		log.debug("Cache hit for " + requestedUri);
		log.trace(cacheNode);

		List<Header> responseHeaders = new ArrayList<Header>();
		if (!cacheNode.has(HTTP.DATE_HEADER)) {
			responseHeaders.add(new BasicHeader(HTTP.DATE_HEADER,
					DateUtils.formatDate(new Date())));
		}
		if (!cacheNode.has(HeaderConstants.CACHE_CONTROL)) {
			responseHeaders.add(new BasicHeader(
					HeaderConstants.CACHE_CONTROL,
					HeaderConstants.CACHE_CONTROL_MAX_AGE + "="
							+ Integer.MAX_VALUE));
		}
		Resource resource = new JarCacheResource(classpath);
		Iterator<String> fieldNames = cacheNode.fieldNames();
		while (fieldNames.hasNext()) {
			String headerName = fieldNames.next();
			JsonNode header = cacheNode.get(headerName);
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

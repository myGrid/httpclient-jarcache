package uk.org.taverna.httpclient.jarcache;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Date;
import java.util.Enumeration;

import org.apache.http.Header;
import org.apache.http.HttpVersion;
import org.apache.http.client.cache.HeaderConstants;
import org.apache.http.client.cache.HttpCacheEntry;
import org.apache.http.client.cache.HttpCacheStorage;
import org.apache.http.client.cache.HttpCacheUpdateCallback;
import org.apache.http.client.cache.HttpCacheUpdateException;
import org.apache.http.client.cache.Resource;
import org.apache.http.impl.client.cache.CacheConfig;
import org.apache.http.impl.client.cache.HeapResource;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicStatusLine;
import org.apache.http.protocol.HTTP;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class JarCacheStorage implements HttpCacheStorage {

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
		System.out.println("Requesting " + key);
		URI requestedUri;
		try {
			requestedUri = new URI(key);
		} catch (URISyntaxException e) {
			return null;
		}
		if ( (requestedUri.getScheme().equals("http") && requestedUri.getPort() == 80) ||
				(requestedUri.getScheme().equals("https") && requestedUri.getPort() == 443) ) {
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
				URI uri = URI.create(node.get("url").asText());
				if (uri.equals(requestedUri)) {
					final URL classpath = new URL(url, node.get("classpath").asText());
					System.out.println("Found it!");
					System.out.println(node);
					

			        Header[] responseHeaders = new Header[] {
			        		new BasicHeader(HTTP.DATE_HEADER, DateUtils.formatDate(new Date())),
			        	new BasicHeader(HeaderConstants.CACHE_CONTROL, HeaderConstants.CACHE_CONTROL_MAX_AGE + "=" + Integer.MAX_VALUE)
			        };
			        // TODO: Content-Type
					Resource resource = new Resource() {
						
						@Override
						public long length() {
							// TODO Auto-generated method stub
							return 0;
						}
						
						@Override
						public InputStream getInputStream() throws IOException {
							return classpath.openStream();
						}
						
						@Override
						public void dispose() {
							// TODO Auto-generated method stub
							
						}
					};
					return new HttpCacheEntry(new Date(), new Date(),
							new BasicStatusLine(HttpVersion.HTTP_1_1, 200, "OK"),
							responseHeaders, resource);

				
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

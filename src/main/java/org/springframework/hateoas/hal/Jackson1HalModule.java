/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.hateoas.hal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.JsonGenerator;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.Version;
import org.codehaus.jackson.map.BeanProperty;
import org.codehaus.jackson.map.ContextualSerializer;
import org.codehaus.jackson.map.DeserializationContext;
import org.codehaus.jackson.map.JsonDeserializer;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.JsonSerializer;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.map.SerializerProvider;
import org.codehaus.jackson.map.TypeSerializer;
import org.codehaus.jackson.map.deser.std.ContainerDeserializerBase;
import org.codehaus.jackson.map.module.SimpleModule;
import org.codehaus.jackson.map.ser.std.ContainerSerializerBase;
import org.codehaus.jackson.map.ser.std.MapSerializer;
import org.codehaus.jackson.map.type.TypeFactory;
import org.codehaus.jackson.type.JavaType;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Resource;
import org.springframework.hateoas.ResourceSupport;
import org.springframework.hateoas.Resources;

/**
 * Jackson 1 module implementation to render {@link Link} and {@link ResourceSupport} instances in HAL compatible JSON.
 * 
 * @author Alexander Baetz
 * @author Oliver Gierke
 */
public class Jackson1HalModule extends SimpleModule {

	/**
	 * Creates a new {@link Jackson1HalModule}.
	 */
	public Jackson1HalModule() {

		super("json-hal-module", new Version(1, 0, 0, null));

		setMixInAnnotation(Link.class, LinkMixin.class);
		setMixInAnnotation(ResourceSupport.class, ResourceSupportMixin.class);
		setMixInAnnotation(Resources.class, ResourcesMixin.class);
	}

	/**
	 * Custom {@link JsonSerializer} to render Link instances in HAL compatible JSON. Renders the list as a map, where
	 * links are sorted based on their relation.
	 * 
	 * @author Alexander Baetz
	 * @author Oliver Gierke
	 */
	public static class HalLinkListSerializer extends ContainerSerializerBase<List<Link>> implements
			ContextualSerializer<List<Link>> {

		private final BeanProperty property;

		/**
		 * Creates a new {@link HalLinkListSerializer}.
		 */
		public HalLinkListSerializer() {
			this(null);
		}

		public HalLinkListSerializer(BeanProperty property) {
			super(List.class, false);
			this.property = property;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.codehaus.jackson.map.ser.std.SerializerBase#serialize(java.lang.Object, org.codehaus.jackson.JsonGenerator,
		 * org.codehaus.jackson.map.SerializerProvider)
		 */
		@Override
		public void serialize(List<Link> value, JsonGenerator jgen, SerializerProvider provider) throws IOException,
				JsonGenerationException {

			// sort links according to their relation
			Map<String, List<Link>> sortedLinks = new HashMap<String, List<Link>>();

			for (Link link : value) {

				if (sortedLinks.get(link.getRel()) == null) {
					sortedLinks.put(link.getRel(), new ArrayList<Link>());
				}

				sortedLinks.get(link.getRel()).add(link);
			}

			TypeFactory typeFactory = provider.getConfig().getTypeFactory();
			JavaType keyType = typeFactory.uncheckedSimpleType(String.class);
			JavaType valueType = typeFactory.constructCollectionType(ArrayList.class, Link.class);
			JavaType mapType = typeFactory.constructMapType(HashMap.class, keyType, valueType);

			MapSerializer serializer = MapSerializer.construct(new String[] {}, mapType, true, null, null,
					provider.findKeySerializer(keyType, null), new OptionalListSerializer(property));

			serializer.serialize(sortedLinks, jgen, provider);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.codehaus.jackson.map.ContextualSerializer#createContextual(org.codehaus.jackson.map.SerializationConfig,
		 * org.codehaus.jackson.map.BeanProperty)
		 */
		@Override
		public JsonSerializer<List<Link>> createContextual(SerializationConfig config, BeanProperty property)
				throws JsonMappingException {
			return new HalLinkListSerializer(property);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.codehaus.jackson.map.ser.std.ContainerSerializerBase#_withValueTypeSerializer(org.codehaus.jackson.map.TypeSerializer)
		 */
		@Override
		public ContainerSerializerBase<?> _withValueTypeSerializer(TypeSerializer vts) {
			return null;
		}
	}

	/**
	 * Custom {@link JsonSerializer} to render {@link Resource}-Lists in HAL compatible JSON. Renders the list as a Map.
	 * 
	 * @author Alexander Baetz
	 * @author Oliver Gierke
	 */
	public static class HalResourcesSerializer extends ContainerSerializerBase<Collection<?>> implements
			ContextualSerializer<Collection<?>> {

		private final BeanProperty property;

		/**
		 * Creates a new {@link HalLinkListSerializer}.
		 */
		public HalResourcesSerializer() {
			this(null);
		}

		public HalResourcesSerializer(BeanProperty property) {
			super(Collection.class, false);
			this.property = property;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.codehaus.jackson.map.ser.std.SerializerBase#serialize(java.lang.Object, org.codehaus.jackson.JsonGenerator,
		 * org.codehaus.jackson.map.SerializerProvider)
		 */
		@Override
		public void serialize(Collection<?> value, JsonGenerator jgen, SerializerProvider provider) throws IOException,
				JsonGenerationException {

			// sort resources according to their types
			Map<String, List<Object>> sortedLinks = new HashMap<String, List<Object>>();

			for (Object resource : value) {

				// TODO: do something fancy to get the relation name
				String relation = "content";
				if (sortedLinks.get(relation) == null) {
					sortedLinks.put(relation, new ArrayList<Object>());
				}

				sortedLinks.get(relation).add(resource);
			}

			TypeFactory typeFactory = provider.getConfig().getTypeFactory();
			JavaType keyType = typeFactory.uncheckedSimpleType(String.class);
			JavaType valueType = typeFactory.constructCollectionType(ArrayList.class, Resource.class);
			JavaType mapType = typeFactory.constructMapType(HashMap.class, keyType, valueType);

			MapSerializer serializer = MapSerializer.construct(new String[] {}, mapType, true, null, null,
					provider.findKeySerializer(keyType, null), new OptionalListSerializer(property));

			serializer.serialize(sortedLinks, jgen, provider);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.codehaus.jackson.map.ContextualSerializer#createContextual(org.codehaus.jackson.map.SerializationConfig,
		 * org.codehaus.jackson.map.BeanProperty)
		 */
		@Override
		public JsonSerializer<Collection<?>> createContextual(SerializationConfig config, BeanProperty property)
				throws JsonMappingException {
			return new HalResourcesSerializer(property);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.codehaus.jackson.map.ser.std.ContainerSerializerBase#_withValueTypeSerializer(org.codehaus.jackson.map.TypeSerializer)
		 */
		@Override
		public ContainerSerializerBase<?> _withValueTypeSerializer(TypeSerializer vts) {
			return null;
		}
	}

	/**
	 * Custom {@link JsonSerializer} to render Objects in HAL compatible JSON. Renders the Object as immediate object if
	 * we have a single one or as array if we have multiple ones.
	 * 
	 * @author Alexander Baetz
	 * @author Oliver Gierke
	 */
	public static class OptionalListSerializer extends ContainerSerializerBase<Object> {

		private final BeanProperty property;
		private JsonSerializer<Object> serializer;

		public OptionalListSerializer() {
			this(null);
		}

		public OptionalListSerializer(BeanProperty property) {

			super(List.class, false);
			this.property = property;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.codehaus.jackson.map.ser.std.ContainerSerializerBase#_withValueTypeSerializer(org.codehaus.jackson.map.TypeSerializer)
		 */
		@Override
		public ContainerSerializerBase<?> _withValueTypeSerializer(TypeSerializer vts) {
			throw new UnsupportedOperationException("Not implemented");
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.codehaus.jackson.map.ser.std.SerializerBase#serialize(java.lang.Object, org.codehaus.jackson.JsonGenerator,
		 * org.codehaus.jackson.map.SerializerProvider)
		 */
		@Override
		public void serialize(Object value, JsonGenerator jgen, SerializerProvider provider) throws IOException,
				JsonGenerationException {

			List<?> list = (List<?>) value;

			if (list.size() == 1) {
				serializeContents(list.iterator(), jgen, provider);
				return;
			}

			jgen.writeStartArray();
			serializeContents(list.iterator(), jgen, provider);
			jgen.writeEndArray();
		}

		private void serializeContents(Iterator<?> value, JsonGenerator jgen, SerializerProvider provider)
				throws IOException, JsonGenerationException {

			while (value.hasNext()) {
				Object elem = value.next();
				if (elem == null) {
					provider.defaultSerializeNull(jgen);
				} else {
					if (serializer == null) {
						serializer = provider.findValueSerializer(elem.getClass(), property);
					}
					serializer.serialize(elem, jgen, provider);
				}
			}
		}
	}

	public class HalLinkListDeserializer extends ContainerDeserializerBase<List<Link>> {

		public HalLinkListDeserializer() {
			super(List.class);
		}

		@Override
		public JavaType getContentType() {
			return null;
		}

		@Override
		public JsonDeserializer<Object> getContentDeserializer() {
			return null;
		}

		@Override
		public List<Link> deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException,
				JsonProcessingException {
			List<Link> result = new ArrayList<Link>();

			Map<String, Object> sortedLinks = null;
			// (Map<String, Object>)
			// ctxt.getDeserializerProvider().findRootValueDeserializer(ctxt.getTypeFactory().constructMapType(HashMap.class,
			// String.class,
			// Object.class)).deserialize(jp, ctxt);

			// TODO do this stuff with a reall content deserializer that handles optional lists
			for (Object curList : sortedLinks.values()) {
				if (curList instanceof List) {
					for (Map<String, String> link : (List<Map>) curList) {
						if (link.keySet().containsAll(Arrays.asList("rel", "href"))) {
							result.add(new Link(link.get("href"), link.get("rel")));
						}
					}
				} else if (curList instanceof Link) {
					result.add((Link) curList);
				} else if (curList instanceof Map) {
					Map<String, String> link = (Map<String, String>) curList;
					if (link.keySet().containsAll(Arrays.asList("rel", "href"))) {
						result.add(new Link(link.get("href"), link.get("rel")));
					}
				} else {
					throw new IllegalArgumentException("unknow content of type : " + curList.getClass());
				}
			}

			return result;
		}
	}
}

package com.opensymphony.sitemesh3.simple;

import com.opensymphony.sitemesh3.SiteMeshContext;
import com.opensymphony.sitemesh3.SiteMeshContextStub;
import com.opensymphony.sitemesh3.content.Content;
import com.opensymphony.sitemesh3.content.ContentProcessor;
import com.opensymphony.sitemesh3.content.ContentProperty;
import com.opensymphony.sitemesh3.content.memory.InMemoryContent;
import com.opensymphony.sitemesh3.content.tagrules.TagBasedContentProcessor;
import com.opensymphony.sitemesh3.content.tagrules.TagRuleBundle;
import com.opensymphony.sitemesh3.content.tagrules.decorate.DecoratorTagRuleBundle;
import com.opensymphony.sitemesh3.content.tagrules.html.CoreHtmlTagRuleBundle;
import com.opensymphony.sitemesh3.tagprocessor.State;
import junit.framework.TestCase;

import java.io.IOException;
import java.nio.CharBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Joe Walnes
 */
public class SimpleConfigPropertiesBuilderTest extends TestCase {

    private SimpleConfig<SiteMeshContext> config;
    private SimpleConfigPropertiesBuilder propertiesBuilder;
    private Map<String, String> properties;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        config = new SimpleConfig<SiteMeshContext>();
        propertiesBuilder = new SimpleConfigPropertiesBuilder(new ObjectFactory.Default());
        properties = new HashMap<String, String>();
    }

    public void testAddsTagRuleBundles() throws SiteMeshConfigException {
        // Sanity check - there should be 2 default bundles already.
        TagRuleBundle[] ruleBundles = config.getTagRuleBundles();
        assertEquals(2, ruleBundles.length);
        assertEquals(CoreHtmlTagRuleBundle.class, ruleBundles[0].getClass());
        assertEquals(DecoratorTagRuleBundle.class, ruleBundles[1].getClass());

        // Configure with param.
        String tagRulesValue = MyTagRuleBundle.class.getName() + ", " + AnotherTagRuleBundle.class.getName();
        properties.put(SimpleConfigPropertiesBuilder.TAG_RULE_BUNDLES_PARAM, tagRulesValue);
        propertiesBuilder.configure(config, properties);

        // There should now be the original 2, plus the 2 new ones.
        ruleBundles = config.getTagRuleBundles();
        assertEquals(4, ruleBundles.length);
        assertEquals(CoreHtmlTagRuleBundle.class, ruleBundles[0].getClass());
        assertEquals(DecoratorTagRuleBundle.class, ruleBundles[1].getClass());
        assertEquals(MyTagRuleBundle.class, ruleBundles[2].getClass());
        assertEquals(AnotherTagRuleBundle.class, ruleBundles[3].getClass());

        // Check that the bundles have actually been added to the ContentProcessor.
        assertEquals(TagBasedContentProcessor.class, config.getContentProcessor().getClass());
        TagBasedContentProcessor contentProcessor = (TagBasedContentProcessor) config.getContentProcessor();
        ruleBundles = contentProcessor.getTagRuleBundles();
        assertEquals(4, ruleBundles.length);
        assertEquals(CoreHtmlTagRuleBundle.class, ruleBundles[0].getClass());
        assertEquals(DecoratorTagRuleBundle.class, ruleBundles[1].getClass());
        assertEquals(MyTagRuleBundle.class, ruleBundles[2].getClass());
        assertEquals(AnotherTagRuleBundle.class, ruleBundles[3].getClass());
    }

    // Supports previous test.
    public static class MyTagRuleBundle implements TagRuleBundle {
        @Override
        public void install(State defaultState, ContentProperty contentProperty, SiteMeshContext siteMeshContext) {
            // no-op.
        }
    }

    // Supports previous test.
    public static class AnotherTagRuleBundle implements TagRuleBundle {
        @Override
        public void install(State defaultState, ContentProperty contentProperty, SiteMeshContext siteMeshContext) {
            // no-op.
        }
    }

    public void testSetsContentProcessor() throws SiteMeshConfigException, IOException {
        properties.put(SimpleConfigPropertiesBuilder.CONTENT_PROCESSOR_PARAM, MyContentProcessor.class.getName());
        propertiesBuilder.configure(config, properties);

        assertEquals(MyContentProcessor.class, config.getContentProcessor().getClass());
        assertSame(MyContentProcessor.MY_RETURNED_CONTENT, config.build(null, null));
    }

    // Supports previous test.
    public static class MyContentProcessor implements ContentProcessor {
        public static final Content MY_RETURNED_CONTENT = new InMemoryContent();

        @Override
        public Content build(CharBuffer data, SiteMeshContext context) throws IOException {
            return MY_RETURNED_CONTENT;
        }
    }

    public void testDoesNotAllowBothContentProcessorAndTagRuleBundlesToBeSet() {
        String tagRulesValue = MyTagRuleBundle.class.getName() + ", " + AnotherTagRuleBundle.class.getName();
        properties.put(SimpleConfigPropertiesBuilder.TAG_RULE_BUNDLES_PARAM, tagRulesValue);
        properties.put(SimpleConfigPropertiesBuilder.CONTENT_PROCESSOR_PARAM, MyContentProcessor.class.getName());
        try {
            propertiesBuilder.configure(config, properties);
            fail("Expected exception");
        } catch (SiteMeshConfigException expected) {
            // Expected!
        }
    }

    public void testSetsMimeTypes() throws SiteMeshConfigException {
        properties.put(SimpleConfigPropertiesBuilder.MIME_TYPES_PARAM, "text/foo, application/x-stuff  \n foo/bar");
        propertiesBuilder.configure(config, properties);

        assertArrayEquals(config.getMimeTypes(), "text/foo", "application/x-stuff", "foo/bar");
    }

    public void testSetsExcludePaths() throws SiteMeshConfigException {
        properties.put(SimpleConfigPropertiesBuilder.EXCLUDE_PARAM, "/bad/*, *.BAD");
        propertiesBuilder.configure(config, properties);

        assertTrue(config.shouldExclude("/bad/foo"));
        assertTrue(config.shouldExclude("so.bad"));
        assertFalse(config.shouldExclude("/good/foo"));
        assertFalse(config.shouldExclude("so.good"));
    }

    public void testAddsDecoratorMappings() throws SiteMeshConfigException, IOException {
        properties.put(SimpleConfigPropertiesBuilder.DECORATOR_MAPPINGS_PARAM, "" +
                "/a/*=/decorator/a," +
                "/b/*=/decorator/b,");
        propertiesBuilder.configure(config, properties);

        Content someContent = new InMemoryContent();
        assertArrayEquals(
                config.selectDecoratorPaths(someContent, new SiteMeshContextStub().withRequestPath("/a/foo")),
                "/decorator/a");
        assertArrayEquals(
                config.selectDecoratorPaths(someContent, new SiteMeshContextStub().withRequestPath("/b/foo")),
                "/decorator/b");
        assertArrayEquals(
                config.selectDecoratorPaths(someContent, new SiteMeshContextStub().withRequestPath("/c/foo"))
                /* nothing */);
    }

    public void testSupportsMultipleDecoratorMappingsForASinglePath() throws SiteMeshConfigException, IOException {
        properties.put(SimpleConfigPropertiesBuilder.DECORATOR_MAPPINGS_PARAM, "" +
                "/a/*=/decorator/a1|/decorator/a2," +
                "/b/*=/decorator/b1|/decorator/b2," +
                "/c/*=/decorator/c,");
        propertiesBuilder.configure(config, properties);

        Content someContent = new InMemoryContent();
        assertArrayEquals(
                config.selectDecoratorPaths(someContent, new SiteMeshContextStub().withRequestPath("/a/foo")),
                "/decorator/a1", "/decorator/a2");
        assertArrayEquals(
                config.selectDecoratorPaths(someContent, new SiteMeshContextStub().withRequestPath("/b/foo")),
                "/decorator/b1", "/decorator/b2");
        assertArrayEquals(
                config.selectDecoratorPaths(someContent, new SiteMeshContextStub().withRequestPath("/c/foo")),
                "/decorator/c");
        assertArrayEquals(
                config.selectDecoratorPaths(someContent, new SiteMeshContextStub().withRequestPath("/d/foo"))
                /* nothing */);
    }

    public void testCanBeHookedUpToOtherObjectFactories() throws SiteMeshConfigException {
        final ContentProcessor aContentProcessor = new MyContentProcessor();
        ObjectFactory someDependencyInjectionFramework = new ObjectFactory() {
            @Override
            public Object create(String className) throws SiteMeshConfigException {
                if (className.equals("foo")) {
                    return aContentProcessor;
                } else {
                    throw new UnsupportedOperationException();
                }
            }
        };

        properties.put(SimpleConfigPropertiesBuilder.CONTENT_PROCESSOR_PARAM, "foo");
        propertiesBuilder = new SimpleConfigPropertiesBuilder(someDependencyInjectionFramework);
        propertiesBuilder.configure(config, properties);

        assertSame(aContentProcessor, config.getContentProcessor());
    }

    public void testDoesNotOverrideDefaultsIfPropertyNotSet() throws SiteMeshConfigException {
        config.setMimeTypes("some/default");
        config.setTagRuleBundles(new MyTagRuleBundle());

        propertiesBuilder.configure(config, properties);

        assertEquals("some/default", config.getMimeTypes()[0]);
        assertEquals(MyTagRuleBundle.class, config.getTagRuleBundles()[0].getClass());
    }

    // Test helpers.

    private void assertArrayEquals(String actual[], String... expected) {
        assertEquals(join(expected), join(actual));
    }

    private String join(String[] strings) {
        StringBuilder result = new StringBuilder();
        for (String string : strings) {
            if (result.length() > 0) {
                result.append(',');
            }
            result.append(string);
        }
        return result.toString();
    }

}

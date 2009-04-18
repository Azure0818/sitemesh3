package com.opensymphony.sitemesh3.simple;

/**
 * Responsible for instantiating objects - converting from strings in a config file to a real instance.
 *
 * <p>Typically you would use the {@link ObjectFactory.Default} implementation,
 * but this can be replaced. e.g. To connect to a dependency injection framework, lookup from
 * a registry, do custom classloading, etc.</p>
 *
 * @see com.opensymphony.sitemesh3.simple.ObjectFactory.Default
 * @author Joe Walnes
 */
public interface ObjectFactory {

    Object create(String name) throws SiteMeshConfigException;

    /**
     * Default implementation of {@link ObjectFactory} that treats the object
     * name as a class name, loading it from the current ClassLoader and instantiating
     * with the default constructor.
     */
    public static class Default implements ObjectFactory {
        @Override
        public Object create(String name) throws SiteMeshConfigException {
            try {
                Class cls = Class.forName(name);
                return cls.newInstance();
            } catch (ClassNotFoundException e) {
                throw new SiteMeshConfigException("Could not instantiate " + name, e);
            } catch (InstantiationException e) {
                throw new SiteMeshConfigException("Could not instantiate " + name, e);
            } catch (IllegalAccessException e) {
                throw new SiteMeshConfigException("Could not instantiate " + name, e);
            }
        }
    }
}

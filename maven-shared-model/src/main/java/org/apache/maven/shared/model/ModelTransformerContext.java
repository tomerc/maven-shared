package org.apache.maven.shared.model;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.shared.model.impl.DefaultModelDataSource;

import java.io.IOException;
import java.util.*;


/**
 * Primary context for this package. Provides methods for doing transforms.
 */
public final class ModelTransformerContext
{

    /**
     * Factories to use for construction of model containers
     */
    private final Collection<ModelContainerFactory> factories;

    /**
     * List of system and environmental properties to use during interpolation
     */
    private final static List<InterpolatorProperty> systemInterpolatorProperties =
        new ArrayList<InterpolatorProperty>();

    static
    {
        for ( Map.Entry<Object, Object> e : System.getProperties().entrySet() )
        {
            systemInterpolatorProperties.add(
                new InterpolatorProperty( "${" + e.getKey() + "}", (String) e.getValue() ) );
        }

        for ( Map.Entry<String, String> e : System.getenv().entrySet() )
        {
            systemInterpolatorProperties.add( new InterpolatorProperty( "${env." + e.getKey() + "}", e.getValue() ) );
        }
    }

    /**
     * Default constructor
     *
     * @param factories model container factories. Value may be null.
     */
    public ModelTransformerContext( Collection<ModelContainerFactory> factories )
    {
        if ( factories == null )
        {
            this.factories = Collections.emptyList();
        }
        else
        {
            this.factories = factories;
        }
    }

    /**
     * Transforms the specified model properties using the specified transformers.
     *
     * @param modelProperties
     * @param modelPropertyTransformers
     * @return transformed model properties
     */
    public static List<ModelProperty> transformModelProperties(List<ModelProperty> modelProperties,
                                                        List<ModelPropertyTransformer> modelPropertyTransformers)
    {
        if(modelProperties == null) {
            throw new IllegalArgumentException("modelProperties: null");
        }
        if(modelPropertyTransformers == null) {
            throw new IllegalArgumentException("modelPropertyTransformers: null");
        }
        
        List<ModelProperty> properties = new ArrayList<ModelProperty>(modelProperties);
        List<ModelPropertyTransformer> transformers = new ArrayList<ModelPropertyTransformer>(modelPropertyTransformers);
        for(ModelPropertyTransformer mpt : transformers) {
            properties = sort(mpt.transform(sort(properties, mpt.getBaseUri())), mpt.getBaseUri());
            if(transformers.indexOf(mpt) == transformers.size() - 1) {
                properties = sort(sort(properties, mpt.getBaseUri()), mpt.getBaseUri());
            }
        }

        return properties;
    }

    /**
     * Transforms and interpolates specified hierarchical list of domain models (inheritence) to target domain model.
     * Unlike ModelTransformerContext#transform(java.util.List, ModelTransformer, ModelTransformer), this method requires
     * the user to add interpolator properties. It's intended to be used by IDEs.
     *
     * @param domainModels           the domain model list to transform
     * @param fromModelTransformer   transformer that transforms from specified domain models to canonical data model
     * @param toModelTransformer     transformer that transforms from canonical data model to returned domain model
     * @param importModels
     * @param interpolatorProperties properties to use during interpolation. @return processed domain model
     * @throws IOException if there was a problem with the transform
     */
    public DomainModel transform(List<DomainModel> domainModels, ModelTransformer fromModelTransformer,
                                 ModelTransformer toModelTransformer,
                                 Collection<ImportModel> importModels, Collection<InterpolatorProperty> interpolatorProperties)
        throws IOException
    {
        List<InterpolatorProperty> properties = new ArrayList<InterpolatorProperty>( interpolatorProperties );

        List<ModelProperty> transformedProperties =
                importModelProperties(importModels, fromModelTransformer.transformToModelProperties( domainModels ));

        List<ModelProperty> emptyTags = new ArrayList<ModelProperty>();
        for(ModelProperty mp: transformedProperties)
        {
            if(isEmptyTag(mp, transformedProperties))
            {   System.out.println("Removing tag: " + mp); 
                emptyTags.add(mp);
            }
        }
        transformedProperties.removeAll(emptyTags);

        String baseUriForModel = fromModelTransformer.getBaseUri();
        List<ModelProperty> modelProperties =
            sort( transformedProperties, baseUriForModel );

        ModelDataSource modelDataSource = new DefaultModelDataSource();
        modelDataSource.init( modelProperties, factories );

        for ( ModelContainerFactory factory : factories )
        {
            for ( String uri : factory.getUris() )
            {
                List<ModelContainer> modelContainers;
                try
                {
                    modelContainers = modelDataSource.queryFor( uri );
                }
                catch ( IllegalArgumentException e )
                {
                    System.out.println( modelDataSource.getEventHistory() );
                    throw new IllegalArgumentException( e );
                }
                List<ModelContainer> removedModelContainers = new ArrayList<ModelContainer>();
                Collections.reverse( modelContainers );
                for ( int i = 0; i < modelContainers.size(); i++ )
                {
                    ModelContainer mcA = modelContainers.get( i );
                    if ( removedModelContainers.contains( mcA ) )
                    {
                        continue;
                    }
                    for ( ModelContainer mcB : modelContainers.subList( i + 1, modelContainers.size() ) )
                    {
                        ModelContainerAction action = mcA.containerAction( mcB );

                        if ( ModelContainerAction.DELETE.equals( action ) )
                        {
                            modelDataSource.delete( mcB );
                            removedModelContainers.add( mcB );
                        }
                        else if ( ModelContainerAction.JOIN.equals( action ) )
                        {
                            try
                            {
                                mcA = modelDataSource.join( mcA, mcB );
                                removedModelContainers.add( mcB );
                            }
                            catch ( DataSourceException e )
                            {
                                System.out.println( modelDataSource.getEventHistory() );
                                e.printStackTrace();
                                throw new IOException( "Failed to join model containers: URI = " + uri +
                                    ", Factory = " + factory.getClass().getName() );
                            }
                        }
                    }
                }
            }
        }

        //interpolator
        List<ModelProperty> mps = modelDataSource.getModelProperties();

        for ( ModelProperty mp : mps )
        {
            InterpolatorProperty ip = mp.asInterpolatorProperty( baseUriForModel );
            if ( ip != null )
            {
                properties.add( ip );
            }
        }

        List<ModelProperty> unresolvedProperties = new ArrayList<ModelProperty>();
        for ( ModelProperty mp : mps )
        {
            if ( !mp.isResolved() )
            {
                unresolvedProperties.add( mp );
            }
        }

        /*
        for ( InterpolatorProperty ip : properties )
        {
            for ( ModelProperty mp : unresolvedProperties )
            {
                  mp.resolveWith(ip);
                  System.out.println(mp);
                 System.out.println("-------------------");
            }
        }
        */
        mps = sort( mps, baseUriForModel );

        try
        {
            DomainModel domainModel = toModelTransformer.transformToDomainModel( mps );
            domainModel.setEventHistory(modelDataSource.getEventHistory());
            return domainModel;
        }
        catch ( IOException e )
        {
            System.out.println( modelDataSource.getEventHistory() );
            e.printStackTrace();
            throw new IOException( e.getMessage() );
        }
    }

    /**
     * Transforms and interpolates specified hierarchical list of domain models (inheritence) to target domain model.
     * Uses standard environmental and system properties for intepolation.
     *
     * @param domainModels         the domain model list to transform
     * @param fromModelTransformer transformer that transforms from specified domain models to canonical data model
     * @param toModelTransformer   transformer that transforms from canonical data model to returned domain model
     * @return processed domain model
     * @throws IOException if there was a problem with the transform
     */
    public DomainModel transform( List<DomainModel> domainModels, ModelTransformer fromModelTransformer,
                                  ModelTransformer toModelTransformer )
        throws IOException
    {
        return this.transform( domainModels, fromModelTransformer, toModelTransformer, null, systemInterpolatorProperties );
    }

    private static List<ModelProperty> importModelProperties(Collection<ImportModel> importModels,
                                                             List<ModelProperty> modelProperties) {
        List<ModelProperty> properties = new ArrayList<ModelProperty>();
        for(ModelProperty mp: modelProperties) {
            if(mp.getUri().endsWith("importModel")) {
                for(ImportModel im : importModels) {
                    if(im.getId().equals(mp.getValue())) {
                        properties.addAll(im.getModelProperties());
                    }
                }
            } else {
                properties.add(mp);
            }
        }
        return properties;
    }

    /**
     * Sorts specified list of model properties. Typically the list contain property information from the entire
     * hierarchy of models, with most specialized model first in the list.
     * <p/>
     * Define Sorting Rules: Sorting also removes duplicate values (same URI) unless the value contains a parent with
     * a #collection (http://apache.org/model/project/dependencyManagement/dependencies#collection/dependency)
     *
     * @param properties unsorted list of model properties. List may not be null.
     * @param baseUri    the base URI of every model property
     * @return sorted list of model properties
     */
    protected static List<ModelProperty> sort( List<ModelProperty> properties, String baseUri )
    {
        if ( properties == null )
        {
            throw new IllegalArgumentException( "properties" );
        }
        LinkedList<ModelProperty> processedProperties = new LinkedList<ModelProperty>();
        List<String> position = new ArrayList<String>();
        boolean projectIsContained = false;

        for ( ModelProperty p : properties )
        {
            String uri = p.getUri();
            String parentUri = uri.substring( 0, uri.lastIndexOf( "/" ) );
            if(parentUri.endsWith("#property") && !parentUri.substring( 0, parentUri.lastIndexOf( "/" )).equals(baseUri))
            {
                parentUri = parentUri.substring( 0, parentUri.lastIndexOf( "/" ) );
            }
            parentUri = parentUri.replaceAll("#property", "");

            if ( !projectIsContained && uri.equals( baseUri ) )
            {
                projectIsContained = true;
                processedProperties.add( p );
                position.add( 0, uri );
            }
            else if ( !position.contains( uri ) || parentUri.contains( "#collection" ) || parentUri.contains( "#set" ) )
            {
                int pst = position.indexOf( parentUri ) + 1;
                if(pst == 0 && !uri.equals(properties.get(0).getUri()) )
                {
                    throw new IllegalArgumentException("Could not locate parent: Parent URI = " + parentUri
                            + ": Child - " + p.toString());
                }
                processedProperties.add( pst, p );
                position.add( pst, uri );
            }
        }
        return processedProperties;
    }
    
    private static boolean isEmptyTag(ModelProperty modelProperty, List<ModelProperty> modelProperties)
    {
        if(modelProperty.getValue() != null)
        {
            int index = modelProperties.indexOf(modelProperty);
            if(index + 1 < modelProperties.size())
            {
                String peekPropertyUri = modelProperties.get(index + 1).getUri();
                if(peekPropertyUri.contains("#property") && peekPropertyUri.startsWith(modelProperty.getUri()) )
                {
                    return false;
                }
            }
            return modelProperty.getValue().trim().equals("");
        }

        String uri = modelProperty.getUri();
        for(ModelProperty mp: modelProperties) {
            if(mp.getUri().startsWith(uri))
            {
                return false;
            }
        }
        return true;
    }
}
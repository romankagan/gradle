/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.api.artifacts.ResolveException;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.DefaultLenientConfiguration;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact.ResolvableArtifact;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.internal.Try;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

class TransformQueue {
    private final Map<ComponentArtifactIdentifier, TransformationResult> artifactResults;
    private final ExecutionGraphDependenciesResolver dependenciesResolver;
    @Nullable
    private final NodeExecutionContext nodeExecutionContext;
    private final boolean isEntryPoint;
    private final BuildOperationQueue<RunnableBuildOperation> actions;
    private final Transformation transformation;

    TransformQueue(
        Transformation transformation,
        BuildOperationQueue<RunnableBuildOperation> actions,
        Map<ComponentArtifactIdentifier, TransformationResult> artifactResults,
        ExecutionGraphDependenciesResolver dependenciesResolver,
        @Nullable NodeExecutionContext nodeExecutionContext,
        boolean isEntryPoint
    ) {
        this.artifactResults = artifactResults;
        this.actions = actions;
        this.transformation = transformation;
        this.dependenciesResolver = dependenciesResolver;
        this.nodeExecutionContext = nodeExecutionContext;
        this.isEntryPoint = isEntryPoint;
    }

    public void artifactAvailable(ResolvableArtifact artifact) {
        ComponentArtifactIdentifier artifactId = artifact.getId();
        // Ensure file is available
        try {
            artifact.getFile();
        } catch (ResolveException e) {
            artifactResults.put(artifactId, new PrecomputedTransformationResult(Try.failure(e)));
            return;
        } catch (RuntimeException e) {
            artifactResults.put(artifactId,
                new PrecomputedTransformationResult(Try.failure(new DefaultLenientConfiguration.ArtifactResolveException("artifacts", transformation.getDisplayName(), "artifact transform", Collections.singleton(e)))));
            return;
        }
        TransformationSubject subject = TransformationSubject.initial(artifact);
        CacheableInvocation<TransformationSubject> invocation = transformation.createInvocation(subject, dependenciesResolver, nodeExecutionContext, actions);
        Optional<Try<TransformationSubject>> cachedResult = invocation.getCachedResult();
        if (cachedResult.isPresent()) {
            artifactResults.put(artifact.getId(), new PrecomputedTransformationResult(cachedResult.get()));
        } else {
            String displayName = "Transform " + subject.getDisplayName() + " with " + transformation.getDisplayName();
            String progressDisplayName = isEntryPoint ? "Transforming " + subject.getDisplayName() + " with " + transformation.getDisplayName() : null;
            TransformationOperation operation = new TransformationOperation(invocation, displayName, progressDisplayName, artifact.getId(), artifactResults);
            actions.add(operation);
        }
    }
}

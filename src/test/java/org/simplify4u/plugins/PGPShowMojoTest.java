/*
 * Copyright 2021 Slawomir Jaranowski
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.simplify4u.plugins;

import java.io.IOException;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.repository.RepositorySystem;
import org.bouncycastle.bcpg.HashAlgorithmTags;
import org.bouncycastle.bcpg.PublicKeyAlgorithmTags;
import org.bouncycastle.openpgp.PGPException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.testng.MockitoTestNGListener;
import org.simplify4u.plugins.keyserver.PGPKeysCache;
import org.simplify4u.plugins.pgp.ArtifactInfo;
import org.simplify4u.plugins.pgp.KeyFingerprint;
import org.simplify4u.plugins.pgp.KeyId;
import org.simplify4u.plugins.pgp.KeyInfo;
import org.simplify4u.plugins.pgp.SignatureCheckResult;
import org.simplify4u.plugins.pgp.SignatureInfo;
import org.simplify4u.plugins.pgp.SignatureStatus;
import org.simplify4u.plugins.pgp.SignatureUtils;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

@Listeners(MockitoTestNGListener.class)
public class PGPShowMojoTest {

    @Mock
    private ArtifactResolver artifactResolver;

    @Mock
    private PGPKeysCache pgpKeysCache;

    @Mock
    private SignatureUtils signatureUtils;

    @Mock
    private RepositorySystem repositorySystem;

    @Mock
    private MavenSession session;

    @InjectMocks
    private PGPShowMojo mojo;


    @Test
    void shouldReturnMojoName() {
        assertThat(mojo.getMojoName()).isEqualTo(PGPShowMojo.MOJO_NAME);
    }

    @DataProvider
    public static Object[] invalidArtifactNames() {
        return new Object[]{null, "test", "test:test", "test:test:1.0:type:class:class"};
    }

    @Test(dataProvider = "invalidArtifactNames")
    void shouldThrowExceptionForInvalidArtifact(String artifact) {

        //given
        mojo.setArtifact(artifact);

        // when, then
        assertThatThrownBy(() -> mojo.execute())
                .isExactlyInstanceOf(PGPMojoException.class)
                .hasRootCause(null)
                .hasMessage("The parameters 'artifact' is miss or in invalid format"
                        + " - groupId:artifactId:version[:packaging[:classifier]]");
    }

    @Test
    void shouldProcessArtifact() throws MojoFailureException, MojoExecutionException, IOException {

        Artifact artifact = TestArtifactBuilder.testArtifact().build();
        Artifact artifactAsc = TestArtifactBuilder.testArtifact().packaging("jar.asc").build();

        //given
        mojo.setArtifact("groupId:artifactId:1.0.0:war");

        when(repositorySystem.createArtifactWithClassifier(anyString(), anyString(), anyString(), anyString(), isNull())).thenReturn(artifact);
        when(artifactResolver.resolveSignatures(anyCollection())).thenReturn(Collections.singletonMap(artifact, artifactAsc));


        SignatureCheckResult signatureCheckResult = aPGPSignatureInfoBuilder()
                .status(SignatureStatus.SIGNATURE_VALID)
                .build();

        when(signatureUtils.checkSignature(any(), any(), any())).thenReturn(signatureCheckResult);

        // when
        mojo.execute();

        // then
        verify(repositorySystem).createArtifactWithClassifier("groupId", "artifactId", "1.0.0", "war", null);
        verify(artifactResolver).resolveArtifact(artifact);
        verify(artifactResolver).resolveSignatures(anyCollection());

        verify(signatureUtils).checkSignature(artifact, artifactAsc, pgpKeysCache);
        verify(signatureUtils).keyAlgorithmName(anyInt());

        verify(pgpKeysCache).init(isNull(), isNull(), eq(false), any());

        verifyNoMoreInteractions(artifactResolver, pgpKeysCache, signatureUtils, repositorySystem);
    }

    @Test
    void shouldProcessArtifactWithPom() throws MojoFailureException, MojoExecutionException, IOException, PGPException {

        Artifact artifact = TestArtifactBuilder.testArtifact().build();

        //given
        mojo.setArtifact("groupId:artifactId:1.0.0:war");
        mojo.setShowPom(true);

        when(repositorySystem.createArtifactWithClassifier(anyString(), anyString(), anyString(), anyString(), isNull()))
                .thenReturn(artifact);

        // when
        mojo.execute();

        // then
        verify(repositorySystem).createArtifactWithClassifier("groupId", "artifactId", "1.0.0", "war", null);
        verify(artifactResolver).resolveArtifact(artifact);
        verify(artifactResolver).resolvePom(artifact);
        verify(artifactResolver).resolveSignatures(anyCollection());


        verify(pgpKeysCache).init(isNull(), isNull(), eq(false), any());

        verifyNoMoreInteractions(artifactResolver, pgpKeysCache, signatureUtils, repositorySystem);
    }

    @Test
    void shouldFailForNotResolvedArtifact() throws IOException {

        Artifact artifact = TestArtifactBuilder.testArtifact().notResolved().build();
        Artifact artifactAsc = TestArtifactBuilder.testArtifact().packaging("jar.asc").build();

        //given
        mojo.setArtifact("groupId:artifactId:1.0.0:war");

        when(repositorySystem.createArtifactWithClassifier(anyString(), anyString(), anyString(), anyString(), isNull()))
                .thenReturn(artifact);

        when(artifactResolver.resolveSignatures(anyCollection()))
                .thenReturn(Collections.singletonMap(artifact, artifactAsc));

        SignatureCheckResult signatureCheckResult = aPGPSignatureInfoBuilder()
                .status(SignatureStatus.ARTIFACT_NOT_RESOLVED)
                .build();

        when(signatureUtils.checkSignature(any(), any(), any())).thenReturn(signatureCheckResult);

        // when
        assertThatThrownBy(() -> mojo.execute())
                .isExactlyInstanceOf(PGPMojoException.class)
                .hasMessage("Some of artifact can't be checked");

        // then
        verify(repositorySystem).createArtifactWithClassifier("groupId", "artifactId", "1.0.0", "war", null);
        verify(artifactResolver).resolveArtifact(artifact);
        verify(artifactResolver).resolveSignatures(anyCollection());

        verify(signatureUtils).keyAlgorithmName(anyInt());

        verify(pgpKeysCache).init(isNull(), isNull(), eq(false), any());

        verifyNoMoreInteractions(artifactResolver, pgpKeysCache, signatureUtils, repositorySystem);
    }

    @Test
    void shouldFailForNotResolvedSignature() throws MojoExecutionException, IOException {

        Artifact artifact = TestArtifactBuilder.testArtifact().build();
        Artifact artifactAsc = TestArtifactBuilder.testArtifact().packaging("jar.asc").build();

        //given
        mojo.setArtifact("groupId:artifactId:1.0.0:war");

        when(repositorySystem.createArtifactWithClassifier(anyString(), anyString(), anyString(), anyString(), isNull()))
                .thenReturn(artifact);

        when(artifactResolver.resolveSignatures(anyCollection()))
                .thenReturn(Collections.singletonMap(artifact, artifactAsc));

        SignatureCheckResult signatureCheckResult = aPGPSignatureInfoBuilder()
                .signature(null)
                .status(SignatureStatus.SIGNATURE_NOT_RESOLVED)
                .build();

        when(signatureUtils.checkSignature(any(), any(), any())).thenReturn(signatureCheckResult);

        // when
        assertThatThrownBy(() -> mojo.execute())
                .isExactlyInstanceOf(PGPMojoException.class)
                .hasMessage("Some of artifact can't be checked");

        // then
        verify(repositorySystem).createArtifactWithClassifier("groupId", "artifactId", "1.0.0", "war", null);
        verify(artifactResolver).resolveArtifact(artifact);
        verify(artifactResolver).resolveSignatures(anyCollection());

        verify(signatureUtils).keyAlgorithmName(anyInt());

        verify(pgpKeysCache).init(isNull(), isNull(), eq(false), any());

        verifyNoMoreInteractions(artifactResolver, pgpKeysCache, signatureUtils, repositorySystem);
    }

    private SignatureCheckResult.SignatureCheckResultBuilder aPGPSignatureInfoBuilder() {
        return SignatureCheckResult.builder()
                .artifact(ArtifactInfo.builder()
                        .groupId("groupId")
                        .artifactId("artifactId")
                        .type("jar")
                        .classifier("classifier")
                        .version("1.0")
                        .build())
                .signature(SignatureInfo.builder()
                        .version(4)
                        .keyId(KeyId.from(0x1234L))
                        .hashAlgorithm(HashAlgorithmTags.MD5)
                        .keyAlgorithm(PublicKeyAlgorithmTags.RSA_GENERAL)
                        .build())
                .key(KeyInfo.builder()
                        .version(4)
                        .fingerprint(new KeyFingerprint("0x12345678901234567890"))
                        .master(new KeyFingerprint("0x09876543210987654321"))
                        .algorithm(PublicKeyAlgorithmTags.RSA_GENERAL)
                        .uids(Collections.singleton("Test uid <uid@example.com>"))
                        .bits(2048)
                        .build());
    }

}

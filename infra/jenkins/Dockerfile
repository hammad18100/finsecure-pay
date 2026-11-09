FROM jenkins/jenkins:lts-jdk21

USER root
ARG TARGETARCH
ARG KUBECTL_VERSION=v1.37.0

COPY --from=maven:3.9-eclipse-temurin-21 /usr/share/maven /usr/share/maven
COPY --from=docker:28-cli /usr/local/bin/docker /usr/local/bin/docker
COPY --from=docker:28-cli /usr/local/libexec/docker/cli-plugins/docker-buildx /usr/local/libexec/docker/cli-plugins/docker-buildx
COPY --from=aquasec/trivy:0.67.2 /usr/local/bin/trivy /usr/local/bin/trivy

RUN ln -s /usr/share/maven/bin/mvn /usr/bin/mvn \
    && curl -fsSLo /usr/local/bin/kubectl "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/${TARGETARCH}/kubectl" \
    && chmod +x /usr/local/bin/kubectl \
    && git config --system --add safe.directory '*'

COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

ENV JAVA_OPTS="-Djenkins.install.runSetupWizard=false -Dhudson.plugins.git.GitSCM.ALLOW_LOCAL_CHECKOUT=true"
ENV CASC_JENKINS_CONFIG=/var/jenkins_home/casc/jenkins.yaml

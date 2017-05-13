#!/bin/bash -e

mvn sonar:sonar -Dsonar.host.url=https://sonarqube.com -Dsonar.login=${SONARQUBE_TOKEN}

#!/bin/bash
# This script will build the project.

jdk_switcher use openjdk10
# do stuff with OpenJDK 10
wget https://github.com/sormuras/bach/raw/master/install-jdk.sh
chmod +x $TRAVIS_BUILD_DIR/install-jdk.sh
export JAVA_HOME=$HOME/openjdk17
$TRAVIS_BUILD_DIR/install-jdk.sh -F 17 --target $JAVA_HOME

if [ "$TRAVIS_PULL_REQUEST" != "false" ]; then
  echo -e "Build Pull Request #$TRAVIS_PULL_REQUEST => Branch [$TRAVIS_BRANCH]"
  ./gradlew -Prelease.useLastTag=true build
elif [ "$TRAVIS_PULL_REQUEST" == "false" ] && [ "$TRAVIS_TAG" == "" ]; then
  echo -e 'Build Branch with Snapshot => Branch ['${TRAVIS_BRANCH}']'
  ./gradlew -Prelease.travisci=true -PbintrayUser="${bintrayUser}" -PbintrayKey="${bintrayKey}" -PsonatypeUsername="${sonatypeUsername}" -PsonatypePassword="${sonatypePassword}" test --stacktrace
elif [ "$TRAVIS_PULL_REQUEST" == "false" ] && [ "$TRAVIS_TAG" != "" ]; then
  echo -e 'Build Branch for Release => Branch ['${TRAVIS_BRANCH}']  Tag ['${TRAVIS_TAG}']'
  ./gradlew -Prelease.travisci=true -Prelease.useLastTag=true -PbintrayUser="${bintrayUser}" -PbintrayKey="${bintrayKey}" -PsonatypeUsername="${sonatypeUsername}" -PsonatypePassword="${sonatypePassword}" final --stacktrace
else
  echo -e 'WARN: Should not be here => Branch ['${TRAVIS_BRANCH}']  Tag ['${TRAVIS_TAG}']  Pull Request ['${TRAVIS_PULL_REQUEST}']'
  ./gradlew -Prelease.useLastTag=true build
fi

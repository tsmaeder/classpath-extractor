package ch.castleridge.mbt.cpe;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.codehaus.plexus.util.xml.Xpp3Dom;

final class MavenCompilerOptionsExtractor {
  List<String> extractFromConfiguration(Xpp3Dom configuration) {

    List<String> args = new ArrayList<>();

    Optional.ofNullable(getChildString(configuration, "release")).ifPresent(release -> {
      args.add("-release");
      args.add(release);
    });
    Optional.ofNullable(getChildString(configuration, "source")).ifPresent(source -> {
      args.add("-source");
      args.add(source);
    });
    Optional.ofNullable(getChildString(configuration, "target")).ifPresent(target -> {
      args.add("-target");
      args.add(target);
    });
    Optional.ofNullable(getChildString(configuration, "encoding")).ifPresent(encoding -> {
      args.add("-encoding");
      args.add(encoding);
    });
    args.addAll(parseCompilerArgs(configuration));
    return args;
  }

  private String getChildString(Xpp3Dom parent, String childName) {
    Xpp3Dom child = parent.getChild(childName);
    if (child == null || child.getValue() == null || child.getValue().trim().isEmpty()) {
      return null;
    }
    return child.getValue().trim();
  }

  private List<String> parseCompilerArgs(Xpp3Dom config) {
    List<String> result = new ArrayList<>();
    String compilerArgument = getChildString(config, "compilerArgument");
    if (compilerArgument != null) {
      result.add(compilerArgument);
    } else {
      Xpp3Dom compilerArgs = config.getChild("compilerArgs");
      if (compilerArgs != null) {
        for (Xpp3Dom child : compilerArgs.getChildren()) {
          if (child != null && child.getValue() != null && !child.getValue().trim().isEmpty()) {
            result.add(child.getValue().trim());
          }
        }
      } else {
        Xpp3Dom compilerArguments = config.getChild("compilerArguments");
        if (compilerArguments != null) {
          for (Xpp3Dom child : compilerArguments.getChildren()) {
            if (child != null) {
              String optionName = "-"+child.getName();
              if (child.getValue() != null && !child.getValue().trim().isEmpty()) {
                String optionValue = child.getValue().trim();
                // -Akey=value is the canonical javac format for annotation processor options.
                if (optionName.startsWith("-A")) {
                  result.add(optionName + "=" + optionValue);
                } else {
                  result.add(optionName);
                  result.add(optionValue);
                }
              } else {
                result.add(optionName);
              }
            }
          }
        }
      }
    }
    return result;
  }
}

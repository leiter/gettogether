#!/usr/bin/env ruby
# Script to configure Xcode project for libjami iOS build
# Adds static libraries, system frameworks, and build settings

require 'securerandom'

PROJECT_FILE = File.expand_path('../iosApp/iosApp.xcodeproj/project.pbxproj', __dir__)
LIBJAMI_DIR = '$(SRCROOT)/Frameworks/libjami-arm64'
CONTRIB_DIR = '$(SRCROOT)/../jami-daemon/contrib/aarch64-apple-darwin/lib'

# Generate unique 24-char hex ID
def gen_id
  SecureRandom.hex(12).upcase
end

# Static libraries to add
STATIC_LIBS = {
  # libjami (from Frameworks/libjami-arm64)
  libjami: ['libjami.a'],

  # pjSIP stack (from contrib)
  pjsip: %w[
    libpjsua2-aarch64-apple-darwin.a
    libpjsua-aarch64-apple-darwin.a
    libpjsip-ua-aarch64-apple-darwin.a
    libpjsip-simple-aarch64-apple-darwin.a
    libpjsip-aarch64-apple-darwin.a
    libpjmedia-codec-aarch64-apple-darwin.a
    libpjmedia-videodev-aarch64-apple-darwin.a
    libpjmedia-audiodev-aarch64-apple-darwin.a
    libpjmedia-aarch64-apple-darwin.a
    libpjnath-aarch64-apple-darwin.a
    libpjlib-util-aarch64-apple-darwin.a
    libpj-aarch64-apple-darwin.a
    libsrtp-aarch64-apple-darwin.a
    libyuv-aarch64-apple-darwin.a
  ],

  # FFmpeg
  ffmpeg: %w[
    libavcodec.a libavformat.a libavdevice.a libavfilter.a
    libavutil.a libswscale.a libswresample.a
    libx264.a libvpx.a
  ],

  # Crypto
  crypto: %w[
    libgnutls.a libnettle.a libhogweed.a libgmp.a
    libcrypto.a libssl.a libtls.a libsecp256k1.a
  ],

  # Audio
  audio: %w[libopus.a libspeex.a libspeexdsp.a],

  # DHT/Network
  network: %w[libopendht.a libdhtnet.a libnatpmp.a libupnp.a],

  # Utilities
  utils: %w[
    libllhttp.a libhttp_parser.a libjsoncpp.a libyaml-cpp.a
    libarchive.a libgit2.a libargon2.a libfmt.a
    libsimdutf.a libixml.a
  ]
}

# System frameworks to add (AudioUnit doesn't exist separately on iOS)
SYSTEM_FRAMEWORKS = %w[
  AVFoundation CoreAudio AudioToolbox
  CoreVideo VideoToolbox CoreMedia CoreFoundation
  Security Foundation SystemConfiguration
  Metal MetalKit QuartzCore CoreGraphics
  Accelerate CoreServices
]

# Read current project file
content = File.read(PROJECT_FILE)

# Track IDs for new entries
file_refs = []
build_files = []

# Add file references for static libraries
puts "Adding static library references..."

STATIC_LIBS.each do |category, libs|
  libs.each do |lib|
    file_ref_id = gen_id
    build_file_id = gen_id

    # Determine path based on library
    if category == :libjami
      path = "Frameworks/libjami-arm64/#{lib}"
    else
      path = "../jami-daemon/contrib/aarch64-apple-darwin/lib/#{lib}"
    end

    file_refs << {
      id: file_ref_id,
      name: lib,
      path: path,
      build_id: build_file_id
    }

    build_files << {
      id: build_file_id,
      file_ref: file_ref_id,
      name: lib
    }

    puts "  + #{lib}"
  end
end

# Add file references for system frameworks
puts "\nAdding system framework references..."

framework_refs = []
framework_build_files = []

SYSTEM_FRAMEWORKS.each do |fw|
  file_ref_id = gen_id
  build_file_id = gen_id

  framework_refs << {
    id: file_ref_id,
    name: "#{fw}.framework",
    build_id: build_file_id
  }

  framework_build_files << {
    id: build_file_id,
    file_ref: file_ref_id,
    name: "#{fw}.framework"
  }

  puts "  + #{fw}.framework"
end

# Build PBXFileReference entries for static libs
static_lib_refs = file_refs.map do |ref|
  "\t\t#{ref[:id]} /* #{ref[:name]} */ = {isa = PBXFileReference; lastKnownFileType = archive.ar; name = \"#{ref[:name]}\"; path = \"#{ref[:path]}\"; sourceTree = \"<group>\"; };"
end.join("\n")

# Build PBXFileReference entries for system frameworks
sys_framework_refs = framework_refs.map do |ref|
  "\t\t#{ref[:id]} /* #{ref[:name]} */ = {isa = PBXFileReference; lastKnownFileType = wrapper.framework; name = #{ref[:name]}; path = System/Library/Frameworks/#{ref[:name]}; sourceTree = SDKROOT; };"
end.join("\n")

# Build PBXBuildFile entries for static libs
static_lib_build_files = build_files.map do |bf|
  "\t\t#{bf[:id]} /* #{bf[:name]} in Frameworks */ = {isa = PBXBuildFile; fileRef = #{bf[:file_ref]} /* #{bf[:name]} */; };"
end.join("\n")

# Build PBXBuildFile entries for frameworks
sys_framework_build_files = framework_build_files.map do |bf|
  "\t\t#{bf[:id]} /* #{bf[:name]} in Frameworks */ = {isa = PBXBuildFile; fileRef = #{bf[:file_ref]} /* #{bf[:name]} */; };"
end.join("\n")

# Insert file references before End PBXFileReference section
content.sub!(
  /\/\* End PBXFileReference section \*\//,
  "#{static_lib_refs}\n#{sys_framework_refs}\n/* End PBXFileReference section */"
)

# Insert build files before End PBXBuildFile section
content.sub!(
  /\/\* End PBXBuildFile section \*\//,
  "#{static_lib_build_files}\n#{sys_framework_build_files}\n/* End PBXBuildFile section */"
)

# Add to Frameworks group (7555FFB0242A642200829871)
frameworks_group_children = (file_refs + framework_refs).map do |ref|
  "\t\t\t\t#{ref[:id]} /* #{ref[:name]} */,"
end.join("\n")

content.sub!(
  /7555FFB0242A642200829871 \/\* Frameworks \*\/ = \{\s*isa = PBXGroup;\s*children = \(\s*\);/m,
  "7555FFB0242A642200829871 /* Frameworks */ = {\n\t\t\tisa = PBXGroup;\n\t\t\tchildren = (\n#{frameworks_group_children}\n\t\t\t);"
)

# Add to Frameworks build phase (7555FF78242A565900829871)
frameworks_build_phase_files = (build_files + framework_build_files).map do |bf|
  "\t\t\t\t#{bf[:id]} /* #{bf[:name]} in Frameworks */,"
end.join("\n")

content.sub!(
  /7555FF78242A565900829871 \/\* Frameworks \*\/ = \{\s*isa = PBXFrameworksBuildPhase;\s*buildActionMask = 2147483647;\s*files = \(\s*\);/m,
  "7555FF78242A565900829871 /* Frameworks */ = {\n\t\t\tisa = PBXFrameworksBuildPhase;\n\t\t\tbuildActionMask = 2147483647;\n\t\t\tfiles = (\n#{frameworks_build_phase_files}\n\t\t\t);"
)

# Update build settings for Debug target (7555FFA6242A565B00829871)
debug_settings_addition = <<-SETTINGS
				LIBRARY_SEARCH_PATHS = (
					"$(inherited)",
					"$(SRCROOT)/Frameworks/libjami-arm64",
					"$(SRCROOT)/../jami-daemon/contrib/aarch64-apple-darwin/lib",
				);
				HEADER_SEARCH_PATHS = (
					"$(inherited)",
					"$(SRCROOT)/Frameworks/libjami-arm64/include",
					"$(SRCROOT)/../jami-daemon/contrib/aarch64-apple-darwin/include",
				);
SETTINGS

# Update OTHER_LDFLAGS for Debug
content.sub!(
  /(7555FFA6242A565B00829871 \/\* Debug \*\/ = \{[^}]*?OTHER_LDFLAGS = \(\s*"\$\(inherited\)",\s*"-framework",\s*Shared,)\s*\);/m,
  "\\1\n\t\t\t\t\t\"-lstdc++\",\n\t\t\t\t\t\"-lc++\",\n\t\t\t\t\t\"-lcompression\",\n\t\t\t\t\t\"-lz\",\n\t\t\t\t\t\"-liconv\",\n\t\t\t\t\t\"-lbz2\",\n\t\t\t\t);"
)

# Update OTHER_LDFLAGS for Release
content.sub!(
  /(7555FFA7242A565B00829871 \/\* Release \*\/ = \{[^}]*?OTHER_LDFLAGS = \(\s*"\$\(inherited\)",\s*"-framework",\s*Shared,)\s*\);/m,
  "\\1\n\t\t\t\t\t\"-lstdc++\",\n\t\t\t\t\t\"-lc++\",\n\t\t\t\t\t\"-lcompression\",\n\t\t\t\t\t\"-lz\",\n\t\t\t\t\t\"-liconv\",\n\t\t\t\t\t\"-lbz2\",\n\t\t\t\t);"
)

# Add LIBRARY_SEARCH_PATHS and HEADER_SEARCH_PATHS after INFOPLIST_FILE for Debug
content.sub!(
  /(7555FFA6242A565B00829871 \/\* Debug \*\/ = \{[^}]*?INFOPLIST_FILE = iosApp\/Info\.plist;)/m,
  "\\1\n#{debug_settings_addition}"
)

# Add LIBRARY_SEARCH_PATHS and HEADER_SEARCH_PATHS after INFOPLIST_FILE for Release
content.sub!(
  /(7555FFA7242A565B00829871 \/\* Release \*\/ = \{[^}]*?INFOPLIST_FILE = iosApp\/Info\.plist;)/m,
  "\\1\n#{debug_settings_addition}"
)

# Update C++ standard in project-level settings (Debug)
content.sub!(
  /(7555FFA3242A565B00829871 \/\* Debug \*\/ = \{[^}]*?)CLANG_CXX_LANGUAGE_STANDARD = "gnu\+\+14";/m,
  "\\1CLANG_CXX_LANGUAGE_STANDARD = \"c++17\";"
)

# Update C++ standard in project-level settings (Release)
content.sub!(
  /(7555FFA4242A565B00829871 \/\* Release \*\/ = \{[^}]*?)CLANG_CXX_LANGUAGE_STANDARD = "gnu\+\+14";/m,
  "\\1CLANG_CXX_LANGUAGE_STANDARD = \"c++17\";"
)

# Write updated project file
File.write(PROJECT_FILE, content)

puts "\n✓ Xcode project updated successfully!"
puts "\nNext steps:"
puts "1. Open iosApp/iosApp.xcodeproj in Xcode"
puts "2. Build the project (Cmd+B)"
puts "3. Fix any remaining linker errors"
puts "\nLibraries added: #{file_refs.count}"
puts "Frameworks added: #{framework_refs.count}"

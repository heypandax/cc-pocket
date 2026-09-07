# One-off release operation for 1.9.7 build 54. This lives on the ops branch only.
require "spaceship"

abort("this operation is pinned to 1.9.7") unless ENV.fetch("VERSION") == "1.9.7"
Spaceship::ConnectAPI.token = Spaceship::ConnectAPI::Token.create(
  key_id: ENV.fetch("ASC_KEY_ID"), issuer_id: ENV.fetch("ASC_ISSUER_ID"),
  filepath: File.join(ENV.fetch("RUNNER_TEMP"), "AuthKey.p8"),
)
app = Spaceship::ConnectAPI::App.find("com.panda.ccpocket") or abort("ASC app not found")
submitted = ARGV == ["submitted"]
abort("expected prepared or submitted") unless submitted || ARGV == ["prepared"]
accepted = %w[WAITING_FOR_REVIEW IN_REVIEW PENDING_DEVELOPER_RELEASE PENDING_APPLE_RELEASE READY_FOR_SALE READY_FOR_DISTRIBUTION]

unless submitted
  version = app.get_app_store_versions(filter: { platform: "IOS", versionString: "1.9.7" }).first
  abort("1.9.7 version not found") unless version && version.version_string == "1.9.7"
  abort("expected PREPARE_FOR_SUBMISSION, got #{version.app_store_state}") unless version.app_store_state == "PREPARE_FOR_SUBMISSION"
  build = Spaceship::ConnectAPI::Build.all(
    app_id: app.id, version: "1.9.7", build_number: "54", platform: "IOS",
    processing_states: "VALID",
  ).first
  abort("VALID, unexpired iOS 1.9.7 build 54 not found") unless build &&
    build.version == "54" && build.app_version == "1.9.7" &&
    build.processing_state == "VALID" && !build.expired
  # deliver with submit_for_review=false only writes metadata. Select the verified
  # binary explicitly, then read the relationship back before submitting anything.
  version.select_build(build_id: build.id)
  puts("Selected verified iOS 1.9.7 build 54 for the editable store version")
end

13.times do |attempt|
  version = app.get_app_store_versions(filter: { platform: "IOS", versionString: "1.9.7" }).first
  abort("1.9.7 version not found") unless version && version.version_string == "1.9.7"
  build = version.get_build
  abort("1.9.7 must have VALID build 54 attached") unless build && build.version == "54" && build.processing_state == "VALID"
  state = version.app_store_state
  puts("App Store 1.9.7 (54): build=#{build.processing_state}, review=#{state}")
  if submitted
    exit(0) if accepted.include?(state)
    sleep(5) if attempt < 12
  else
    abort("expected PREPARE_FOR_SUBMISSION, got #{state}") unless state == "PREPARE_FOR_SUBMISSION"
    exit(0)
  end
end
abort("App Store review submission did not become visible")

# Desktop module interfaces

The Windows shell is Java 21 + JavaFX. Android remains an independent build. Eighteen platform-independent Java files under `app/src/local/grworld/studio` supply the existing camera policies, JPEG validation, SD transactions, deployment ownership and report reconciliation; the desktop build compiles the same files without Android dependencies.

| Contract | Responsibility |
| --- | --- |
| `FeatureModule` | Versioned module id, title, description and JavaFX view |
| `FeatureHost` | Shared services, serialized background tasks, feedback, identity refresh and home navigation |
| `CameraTransport` | Read-only identity and explicit upload, with a network-session key |
| `CardProvider` / `CardAccess` | Enumerated removable volumes, identity-bound relative file operations |
| `ImageProcessor` | Source decoding and exact output JPEG generation |
| `TaskStore` | Durable local records; modules use their own key prefix |

Interfaces live in `desktop/src/local/grworld/studio/spi`. Version 1 modules implement `FeatureModule.apiVersion() == 1`. The shell rejects unsupported versions and duplicate ids. Built-in modules use the same registry as service-provided modules.

## Add a module

1. Add an implementation under `desktop/src`. Keep business logic outside JavaFX controls and request services through `FeatureHost`.
2. Add its fully qualified class name to `desktop/resources/META-INF/services/local.grworld.studio.spi.FeatureModule`. The build copies resources into the application JAR; `ServiceLoader` discovers the module at startup.
3. Return the module's view from `createView(host)`. Use `host.runTask` for blocking operations and update the view in its completion callback. The shell provides the homepage entry.
4. Define exact supported identities, necessary preconditions, external recovery evidence and a user-visible result. A completed local task is never proof of camera execution.

Modules are compiled into the application, not downloaded at runtime. `DesktopSession` coordinates the built-in workflows; `DesktopCamera`, `DesktopCard`, `DesktopImages` and `DesktopStore` implement platform services. Windows card writes reject ordinary folders and system volumes. Image previews decode the exact output bytes. A test-only process explicitly labelled `--ui-test` substitutes synthetic identity and an isolated card directory; normal startup always uses the production transport.

The desktop build does not alter Android files. Shared-core changes require validation of both applications. Bundled dependency licenses are retained under `app/legal` and `runtime/legal`.

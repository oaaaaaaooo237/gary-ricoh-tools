package local.grworld.studio;

/** Session context comes only from an observed camera, never a guessed default.
 * Retaining it across a disconnect permits the already-identified SD task to continue;
 * it is not a claim that the camera is currently connected. */
final class WorkflowSession {
 private CameraIdentity identified;
 CameraIdentity context(CameraIdentity live){if(live!=null)identified=live;return identified;}
 int route(CameraIdentity live){return CameraWorkflowPolicy.route(context(live));}
 static int edition(CameraIdentity camera){if(camera==null||!camera.sdSupported())return -1;return camera.model.equals("RICOH GR IIIx Urban Edition")?1:camera.model.equals("RICOH GR IIIx HDF")?2:0;}
}

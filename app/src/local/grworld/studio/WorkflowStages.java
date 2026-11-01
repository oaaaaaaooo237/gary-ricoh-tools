package local.grworld.studio;

/** Mandatory stages; old cached flags cannot open future steps after a resume. */
final class WorkflowStages {
 static int enhancement(boolean identity,boolean task,boolean reconcile,boolean report,boolean visual){return !identity?0:!task?1:reconcile?2:!report?3:!visual?4:5;}
 static int wifi(boolean identity,boolean image,boolean pending,boolean recovery){return pending?(recovery?3:4):!identity?0:!image?1:2;}
 static String enhancementTitle(int step){return new String[]{"第 1 步 · 自动识别相机","第 2 步 · 选择模块并准备 SD 卡","续办检查 · 读取 SD 实际状态","第 3 步 · 在相机执行并读取结果","第 4 步 · 检查相机实际效果","本轮核对完成"}[step];}
}

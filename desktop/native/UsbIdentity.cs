using System;
using System.IO;
using System.Collections.Generic;
using System.Linq;
using System.Runtime.InteropServices;
using System.Text;

namespace GaryRicohTools {
 // Windows SDK PortableDeviceApi.h / PortableDeviceTypes.h ABI. This wrapper
 // exposes only enumeration, property reads and two fixed capability queries.
 [StructLayout(LayoutKind.Sequential)] public struct Key {
  public Guid Format; public uint Id;
  public Key(string g,uint n) {Format=new Guid(g);Id=n;}
 }
 [StructLayout(LayoutKind.Explicit,Size=24)] public struct Variant {
  [FieldOffset(0)] public ushort Type;
  [FieldOffset(8)] public uint UInt;
  [FieldOffset(8)] public ulong ULong;
 }
 [ComImport,Guid("a1567595-4c2f-4574-a6fa-ecef917b9a40"),InterfaceType(ComInterfaceType.InterfaceIsIUnknown)] interface IManager {
  [PreserveSig] int GetDevices(IntPtr ids,ref uint count);
  [PreserveSig] int Refresh();
  [PreserveSig] int Friendly([MarshalAs(UnmanagedType.LPWStr)]string id,[Out,MarshalAs(UnmanagedType.LPWStr)]StringBuilder b,ref uint count);
 }
 [ComImport,Guid("625e2df8-6392-4cf0-9ad1-3cfa5f17775c"),InterfaceType(ComInterfaceType.InterfaceIsIUnknown)] interface IDevice {
  [PreserveSig] int Open([MarshalAs(UnmanagedType.LPWStr)]string id,IValues client);
  [PreserveSig] int Send(uint flags,IValues parameters,out IValues results);
  [PreserveSig] int Content(out IContent content);
  [PreserveSig] int Capabilities(out IntPtr capabilities);
  [PreserveSig] int Cancel();
  [PreserveSig] int Close();
 }
 [ComImport,Guid("6848f6f2-3155-4f86-b6f5-263eeeab3143"),InterfaceType(ComInterfaceType.InterfaceIsIUnknown)] interface IValues {
  [PreserveSig] int Count(out uint count);
  [PreserveSig] int At(uint index,out Key key,out Variant value);
  [PreserveSig] int Set(ref Key key,ref Variant value);
  [PreserveSig] int Get(ref Key key,out Variant value);
  [PreserveSig] int SetString(ref Key key,[MarshalAs(UnmanagedType.LPWStr)]string value);
  [PreserveSig] int GetString(ref Key key,[MarshalAs(UnmanagedType.LPWStr)]out string value);
  [PreserveSig] int SetUInt(ref Key key,uint value);
  [PreserveSig] int GetUInt(ref Key key,out uint value);
  void Unused08(); void Unused09(); void Unused10();
  [PreserveSig] int GetULong(ref Key key,out ulong value);
  void Unused12(); void Unused13(); void Unused14(); void Unused15();
  void Unused16(); void Unused17(); void Unused18(); void Unused19();
  void Unused20(); void Unused21();
  [PreserveSig] int SetUnknown(ref Key key,[MarshalAs(UnmanagedType.IUnknown)]object value);
  [PreserveSig] int GetUnknown(ref Key key,[MarshalAs(UnmanagedType.IUnknown)]out object value);
  [PreserveSig] int SetGuid(ref Key key,ref Guid value);
  void Unused25();
  [PreserveSig] int SetBuffer(ref Key key,[MarshalAs(UnmanagedType.LPArray,SizeParamIndex=2)]byte[] buffer,uint length);
  [PreserveSig] int GetBuffer(ref Key key,out IntPtr buffer,out uint length);
 }
 [ComImport,Guid("6a96ed84-7c73-4480-9938-bf5af477d426"),InterfaceType(ComInterfaceType.InterfaceIsIUnknown)] interface IContent {
  void UnusedEnum();
  [PreserveSig] int Properties(out IProperties properties);
 }
 [ComImport,Guid("7f6d695c-03df-4439-a809-59266beee3a6"),InterfaceType(ComInterfaceType.InterfaceIsIUnknown)] interface IProperties {
  void UnusedSupported(); void UnusedAttributes();
  [PreserveSig] int Values([MarshalAs(UnmanagedType.LPWStr)]string objectId,IntPtr keys,out IValues values);
 }
 [ComImport,Guid("89b2e422-4f1b-4316-bcef-a44afea83eb3"),InterfaceType(ComInterfaceType.InterfaceIsIUnknown)] interface IVariants {
  [PreserveSig] int Count(out uint n);
  [PreserveSig] int At(uint i,out Variant value);
  [PreserveSig] int Add(ref Variant value);
 }
 public sealed class UsbCamera { public string Id,Name; }
 public sealed class NativeDeviceInfo {
  public string Model="",Firmware="";
  public string[] Operations=new string[0],DeviceProperties=new string[0];
  public bool AdvertisesStandardUpload {get{return Operations.Contains("0x100C") && Operations.Contains("0x100D");}}
 }
 public static partial class UsbProbe {
  static readonly string Common="F0422A9C-5DC8-4440-B5BD-5DF28835658A", Vendor="4D545058-1A2E-4106-A357-771E0819FC56";
  static void Check(int hr) {if(hr<0) Marshal.ThrowExceptionForHR(hr);}
  static T Create<T>(string cls) {return (T)Activator.CreateInstance(Type.GetTypeFromCLSID(new Guid(cls)));}
  static void Release(object o) {if(o!=null && Marshal.IsComObject(o)) Marshal.ReleaseComObject(o);}
  static IValues NewValues() {return Create<IValues>("0c15d503-d017-47ce-9016-7b3f978721cc");}
  public static List<UsbCamera> Enumerate() {
   var output=new List<UsbCamera>(); IManager manager=null; IntPtr ids=IntPtr.Zero; uint count=0,allocated=0;
   try {
    manager=Create<IManager>("0af10cec-2ecd-4b92-9581-34f6ae0637f3"); Check(manager.Refresh());
    Check(manager.GetDevices(IntPtr.Zero,ref count)); if(count==0)return output;
    if(count>256)throw new InvalidOperationException("便携设备数量异常");
    allocated=count; ids=Marshal.AllocCoTaskMem(checked((int)count*IntPtr.Size));
    for(int i=0;i<allocated;i++) Marshal.WriteIntPtr(ids,i*IntPtr.Size,IntPtr.Zero);
    Check(manager.GetDevices(ids,ref count));
    if(count>allocated)throw new InvalidOperationException("设备列表已变化，请重新检测");
    for(int i=0;i<count;i++) {
     string id=Marshal.PtrToStringUni(Marshal.ReadIntPtr(ids,i*IntPtr.Size));
     if(id==null || id.IndexOf("VID_25FB",StringComparison.OrdinalIgnoreCase)<0)continue;
     uint length=512; var name=new StringBuilder((int)length);
     string label=manager.Friendly(id,name,ref length)>=0 ? name.ToString() : "RICOH USB 相机";
     output.Add(new UsbCamera {Id=id,Name=label});
    }
    return output;
   } finally {
    if(ids!=IntPtr.Zero) {for(int i=0;i<allocated;i++){var p=Marshal.ReadIntPtr(ids,i*IntPtr.Size);if(p!=IntPtr.Zero)Marshal.FreeCoTaskMem(p);}Marshal.FreeCoTaskMem(ids);}
    Release(manager);
   }
  }
  internal static void ValidateIdentityModel(string model,string firmware) {
   if(model!="RICOH GR IV HDF" || firmware!="1.20")
    throw new InvalidOperationException("USB identity currently supports GR IV HDF 1.20 only");
  }
  static IValues Command(uint id) {
   if(id!=13 && id!=15 && id!=17)throw new InvalidOperationException("只允许读取和结束读取命令");
   var args=NewValues();try{var cat=new Key(Common,1001);var key=new Key(Common,1002);var guid=new Guid(Vendor);Check(args.SetGuid(ref cat,ref guid));Check(args.SetUInt(ref key,id));return args;}catch{Release(args);throw;}
  }
  static IValues SendRead(IDevice device,IValues args) {
   IValues results=null;
   try {
    Check(device.Send(0,args,out results));Variant value;var hr=new Key(Common,1003);Check(results.Get(ref hr,out value));
    if(value.Type!=10 && value.Type!=19)throw new InvalidOperationException("USB 返回状态类型不符");
    Check(unchecked((int)value.UInt));return results;
   }catch{Release(results);throw;}
  }
  internal static NativeDeviceInfo ParseDeviceInfo(byte[] bytes) {
   if(bytes==null || bytes.Length>16384)throw new InvalidDataException("设备能力数据超限");
   using(var s=new MemoryStream(bytes))using(var r=new BinaryReader(s)) {
    r.ReadUInt16();r.ReadUInt32();r.ReadUInt16();PtpString(r);r.ReadUInt16();
    var operations=PtpArray(r);PtpArray(r);var properties=PtpArray(r);PtpArray(r);PtpArray(r);
    PtpString(r);var model=PtpString(r);var firmware=PtpString(r);PtpString(r); // Discard serial; never export it.
    if(s.Position!=s.Length)throw new InvalidDataException("设备能力数据包含未识别尾部");
    return new NativeDeviceInfo {Model=model,Firmware=firmware,Operations=operations,DeviceProperties=properties};
   }
  }
  static string PtpString(BinaryReader r) {
   int count=r.ReadByte();if(count==0)return "";
   var b=r.ReadBytes(count*2);if(b.Length!=count*2 || b[b.Length-1]!=0 || b[b.Length-2]!=0)throw new InvalidDataException("USB 字符串不完整");
   return Encoding.Unicode.GetString(b,0,b.Length-2);
  }
  static string[] PtpArray(BinaryReader r) {
   uint count=r.ReadUInt32();if(count>256)throw new InvalidDataException("USB 能力条目过多");
   var values=new string[count];for(uint i=0;i<count;i++)values[i]="0x"+r.ReadUInt16().ToString("X4");return values;
  }
  // Fixed, standard GetDeviceInfo. No upload/format/delete opcode is exposed.
  static NativeDeviceInfo ReadNativeInfo(IDevice device,string model,string firmware) {
   ValidateIdentityModel(model,firmware);
   IVariants parameters=null;IValues args=null,result=null;string context=null;bool ended=false;
   try {
    parameters=Create<IVariants>("08a99e2f-6d6d-4b80-af5a-baf2bcbe4cb9");
    args=Command(13);var opcode=new Key(Vendor,1001);var operationParams=new Key(Vendor,1002);
    Check(args.SetUInt(ref opcode,0x1001));Check(args.SetUnknown(ref operationParams,parameters));result=SendRead(device,args);
    var contextKey=new Key(Vendor,1006);Check(result.GetString(ref contextKey,out context));
    if(string.IsNullOrEmpty(context) || context.Length>4096)throw new InvalidDataException("USB 读取上下文无效");
    ulong total;var totalKey=new Key(Vendor,1007);Check(result.GetULong(ref totalKey,out total));
    if(total==0 || total>16384)throw new InvalidDataException("USB 设备能力长度超限");
    Release(result);result=null;Release(args);args=null;
    args=Command(15);Check(args.SetString(ref contextKey,context));var wanted=new Key(Vendor,1008);Check(args.SetUInt(ref wanted,(uint)total));
    var dataKey=new Key(Vendor,1012);Check(args.SetBuffer(ref dataKey,new byte[(int)total],(uint)total));result=SendRead(device,args);
    uint actual;var actualKey=new Key(Vendor,1009);Check(result.GetUInt(ref actualKey,out actual));
    IntPtr pointer=IntPtr.Zero;uint bufferLength;byte[] bytes;
    Check(result.GetBuffer(ref dataKey,out pointer,out bufferLength));
    try {
     if(actual!=total || bufferLength<actual || bufferLength>65536 || pointer==IntPtr.Zero)throw new InvalidDataException("USB 设备能力数据不完整");
     bytes=new byte[(int)total];Marshal.Copy(pointer,bytes,0,(int)total);
    }finally{if(pointer!=IntPtr.Zero)Marshal.FreeCoTaskMem(pointer);}
    Release(result);result=null;Release(args);args=null;
    args=Command(17);Check(args.SetString(ref contextKey,context));result=SendRead(device,args);ended=true;
    uint response;var responseKey=new Key(Vendor,1003);Check(result.GetUInt(ref responseKey,out response));
    if(response!=0x2001)throw new InvalidDataException("相机拒绝设备能力读取：0x"+response.ToString("X4"));
    var data=ParseDeviceInfo(bytes);
    if(data.Model!=model || data.Firmware!=firmware)throw new InvalidDataException("原生 USB 与 Windows 返回的机型或版本不一致");
    return data;
   }finally {
    Release(result);Release(args);Release(parameters);
    if(context!=null && !ended) {
     IValues cleanup=null,response=null;
     try{cleanup=Command(17);var key=new Key(Vendor,1006);Check(cleanup.SetString(ref key,context));response=SendRead(device,cleanup);}catch{}finally{Release(response);Release(cleanup);}
    }
   }
  }
  public static NativeDeviceInfo Identify() {
   var cameras=Enumerate();if(cameras.Count!=1)throw new InvalidOperationException("Connect exactly one RICOH USB camera");
   IDevice device=null;IValues client=null,props=null;IContent content=null;IProperties properties=null;bool opened=false;
   try {
    device=Create<IDevice>("728a21c5-3d9e-48d7-9810-864848f0f404");client=NewValues();
    var access=new Key("204D9F0C-2292-4080-9F42-40664E70F859",9);Check(client.SetUInt(ref access,0xc0000000));
    Check(device.Open(cameras[0].Id,client));opened=true;
    Check(device.Content(out content));Check(content.Properties(out properties));Check(properties.Values("DEVICE",IntPtr.Zero,out props));
    string model,firmware;var key=new Key("26D4979A-E643-4626-9E2B-736DC0C92FDC",8);Check(props.GetString(ref key,out model));
    key=new Key("26D4979A-E643-4626-9E2B-736DC0C92FDC",3);Check(props.GetString(ref key,out firmware));
    return ReadNativeInfo(device,model,firmware);
   }finally {if(opened)device.Close();Release(props);Release(properties);Release(content);Release(client);Release(device);}
  }
 }
 static class UsbIdentityEntry {
  [STAThread] public static int Main(string[] args) {
   if(args.Length!=0)return 2;
   try { var identity=UsbProbe.Identify();Console.WriteLine(new System.Web.Script.Serialization.JavaScriptSerializer().Serialize(new {Model=identity.Model,Firmware=identity.Firmware,ReadOpcode="0x1001",CameraWriteRequests=0}));return 0; }
   catch(Exception e){Console.WriteLine(new System.Web.Script.Serialization.JavaScriptSerializer().Serialize(new {Error=e.Message}));return 1;}
  }
 }
}

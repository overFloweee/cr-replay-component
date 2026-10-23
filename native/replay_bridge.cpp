// SPDX-License-Identifier: Apache-2.0
// Adapted for CR Replay Component from fixed main d815abb34bbb3847ca2fec95df3096059ea7dda0; see docs/provenance.md.
#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <sys/mman.h>
#include <unistd.h>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <string>
#include <cstdio>
#include <atomic>
#include <link.h>
#include <elf.h>
#include <time.h>
#ifdef CR_REPLAY_TESTING
#include <sys/system_properties.h>
#endif
#define LOG(...) __android_log_print(ANDROID_LOG_INFO,"CRReplay",__VA_ARGS__)
namespace {
uintptr_t base=0;
std::mutex lock;
std::string pending,completion;
int owner=-1;
int last_progress_tick=-1;
uint32_t identity[2]={0,0};
int actual_crowns[2]={0,0};
bool loading=false,submitted=false,installed=false;
std::atomic<bool> finished{false};
std::atomic<bool> exit_requested{false};
std::atomic<bool> battle_rendering{false},frame_probe{false};
std::atomic<int> presented_frames{0};
std::atomic<int64_t> submitted_ns{0},first_frame_ns{0};
int64_t monotonic_ns(){timespec time{};clock_gettime(CLOCK_MONOTONIC,&time);return static_cast<int64_t>(time.tv_sec)*1000000000+time.tv_nsec;}
using Swap=int(*)(void*,void*);
Swap original_swap=nullptr;
int observed_swap(void* display,void* surface){int ok=original_swap(display,surface);if(ok&&battle_rendering.load(std::memory_order_acquire)){int64_t empty=0;first_frame_ns.compare_exchange_strong(empty,monotonic_ns());presented_frames.fetch_add(1,std::memory_order_relaxed);}return ok;}
// 按符号查找当前引擎的 GOT；不复用另一个引擎的固定地址。
int install_frame_probe(dl_phdr_info* info,size_t,void* handle){
 if(info->dlpi_addr!=base)return 0;
 const ElfW(Dyn)* dynamic=nullptr;uintptr_t relro_begin=0,relro_end=0;
 for(int i=0;i<info->dlpi_phnum;i++){const auto& h=info->dlpi_phdr[i];if(h.p_type==PT_DYNAMIC)dynamic=reinterpret_cast<const ElfW(Dyn)*>(base+h.p_vaddr);if(h.p_type==PT_GNU_RELRO){relro_begin=base+h.p_vaddr;relro_end=relro_begin+h.p_memsz;}}
 if(!dynamic)return 1;const ElfW(Sym)* symbols=nullptr;const char* strings=nullptr;const ElfW(Rela)* relocs=nullptr;size_t bytes=0;bool rela=false;
 auto pointer=[](uintptr_t value){return value<base?base+value:value;};
 for(auto* d=dynamic;d->d_tag!=DT_NULL;d++){switch(d->d_tag){case DT_SYMTAB:symbols=reinterpret_cast<const ElfW(Sym)*>(pointer(d->d_un.d_ptr));break;case DT_STRTAB:strings=reinterpret_cast<const char*>(pointer(d->d_un.d_ptr));break;case DT_JMPREL:relocs=reinterpret_cast<const ElfW(Rela)*>(pointer(d->d_un.d_ptr));break;case DT_PLTRELSZ:bytes=d->d_un.d_val;break;case DT_PLTREL:rela=d->d_un.d_val==DT_RELA;break;}}
 if(!symbols||!strings||!relocs||!rela||bytes>4*1024*1024)return 1;
 for(size_t i=0;i<bytes/sizeof(ElfW(Rela));i++){const auto& r=relocs[i];if(ELF64_R_TYPE(r.r_info)!=R_AARCH64_JUMP_SLOT||std::strcmp(strings+symbols[ELF64_R_SYM(r.r_info)].st_name,"eglSwapBuffers"))continue;
  auto** slot=reinterpret_cast<void**>(base+r.r_offset);void* expected=dlsym(handle,"eglSwapBuffers");if(!expected||*slot!=expected)return 1;
  const uintptr_t page=static_cast<uintptr_t>(sysconf(_SC_PAGESIZE)),address=reinterpret_cast<uintptr_t>(slot),start=address&~(page-1);
  if(mprotect(reinterpret_cast<void*>(start),page,PROT_READ|PROT_WRITE))return 1;original_swap=reinterpret_cast<Swap>(expected);__atomic_store_n(slot,reinterpret_cast<void*>(&observed_swap),__ATOMIC_RELEASE);
  mprotect(reinterpret_cast<void*>(start),page,address>=relro_begin&&address<relro_end?PROT_READ:PROT_READ|PROT_WRITE);frame_probe.store(true,std::memory_order_release);return 1;
 }return 1;
}
using Lifecycle=void(*)(void*,float);
using Transition=void(*)(void*);
using Event=void(*)(void*,int,int,void*,void*,void*);
Lifecycle original_lifecycle=nullptr;
Transition original_transition=nullptr;
Event original_event=nullptr;
template<class T> T read(void* p,size_t n){T value;std::memcpy(&value,static_cast<char*>(p)+n,sizeof(value));return value;}
template<class T> void write(void* p,size_t n,T value){std::memcpy(static_cast<char*>(p)+n,&value,sizeof(value));}
template<class F> F at(uintptr_t n){return reinterpret_cast<F>(base+n);}
void jump(void* address,const void* target){uint32_t instructions[]={0x58000051,0xd61f0220};std::memcpy(address,instructions,8);std::memcpy(static_cast<char*>(address)+8,&target,8);}
bool hook(uintptr_t offset,const uint8_t (&bytes)[16],const void* replacement,void** original){
 auto* target=reinterpret_cast<uint8_t*>(base+offset);
 if(std::memcmp(target,bytes,16))return false;
 void* trampoline=mmap(nullptr,32,PROT_READ|PROT_WRITE,MAP_PRIVATE|MAP_ANONYMOUS,-1,0);
 if(trampoline==MAP_FAILED)return false;
 std::memcpy(trampoline,target,16);jump(static_cast<char*>(trampoline)+16,target+16);
 if(mprotect(trampoline,32,PROT_READ|PROT_EXEC)){munmap(trampoline,32);return false;}
 __builtin___clear_cache(static_cast<char*>(trampoline),static_cast<char*>(trampoline)+32);
 const auto page=static_cast<uintptr_t>(sysconf(_SC_PAGESIZE));const auto begin=reinterpret_cast<uintptr_t>(target)&~(page-1);const auto length=((reinterpret_cast<uintptr_t>(target)+16+page-1)&~(page-1))-begin;
 if(mprotect(reinterpret_cast<void*>(begin),length,PROT_READ|PROT_WRITE|PROT_EXEC)){munmap(trampoline,32);return false;}
 *original=trampoline;jump(target,replacement);__builtin___clear_cache(reinterpret_cast<char*>(target),reinterpret_cast<char*>(target+16));
 return mprotect(reinterpret_cast<void*>(begin),length,PROT_READ|PROT_EXEC)==0;
}
void event(void* app,int type,int argument,void* a,void* b,void* c){
 // 2/3/4 是连接失败/中断，8 是登录失败；离线播放器没有在线会话。
 if(type==2||type==3||type==4||type==8)return;
 original_event(app,type,argument,a,b,c);
}
bool result(void* game){
 void* world=read<void*>(game,0xa8);if(!world||!read<uint8_t>(world,0x1e0))return false;
 for(int i=0;i<2;i++){void* id=read<void*>(world,0x30+8*i);if(id&&read<uint32_t>(id,0)==identity[0]&&read<uint32_t>(id,4)==identity[1])owner=i;}
 if(owner<0)return false;
 void* king=read<void*>(world,0xe0);if(!king)return false;
 void* objects=read<void*>(king,0x10);if(!objects)return false;
 const int n=read<int>(objects,0x14);void* vector=read<void*>(objects,8);if(!vector||n<0||n>2000)return false;
 int remaining[2]={0,0},kings[2]={0,0};bool alive[2]={false,false};
 for(int i=0;i<n;i++){
  void* o=read<void*>(vector,8*i);if(!o||read<int>(o,0xac)!=-1)continue;
  const int side=read<int>(o,0x78);if(side<0||side>1)continue;
  void* type=read<void*>(o,0);void* kind=type?read<void*>(type,0x10):nullptr;
  if(!kind||reinterpret_cast<int(*)(void*)>(kind)(o)!=5)continue;
  void* data=read<void*>(o,0x48);if(!data)continue;
  // 当前固定核心的冠塔标志；特殊棋盘的王不在 X=9000。
  const bool king=read<uint8_t>(data,0x582),tower=read<uint8_t>(data,0x581);
  if(!king&&!tower)continue;
  void* parts=read<void*>(o,0x18);const int count=read<int>(o,0x24);
  if(!parts||count<3||count>128||read<int>(o,0x20)<count)return false;
  void* health=read<void*>(parts,16);if(!health||read<void*>(health,8)!=o)return false;
  if(king)kings[side]++;
  if(read<int>(health,0x10)>0){remaining[side]++;if(king)alive[side]=true;}
 }
 if(kings[0]!=1||kings[1]!=1)return false;
 int crowns[2]={alive[1]?3-remaining[1]:3,alive[0]?3-remaining[0]:3};if(crowns[0]<0||crowns[0]>3||crowns[1]<0||crowns[1]>3)return false;
 actual_crowns[0]=crowns[0];actual_crowns[1]=crowns[1];
 char text[192];std::snprintf(text,sizeof(text),"{\"ownCrowns\":%d,\"opponentCrowns\":%d,\"winner\":%d,\"tick\":%d}",crowns[owner],crowns[1-owner],read<int>(world,0x1b8),read<int>(game,0x60));completion=text;finished.store(true,std::memory_order_release);LOG("modern result %s",text);return true;
}
using Presentation=void(*)(void*,void*);
Presentation original_presentation=nullptr;
void presentation(void* screen,void* world){
 std::unique_lock<std::mutex> guard(lock);
 void* manager=at<void*(*)()>(0xed099c)();void* controller=manager?read<void*>(manager,0x28):nullptr;void* game=controller?read<void*>(controller,0x90):nullptr;
 if(!screen||!world||!game||read<void*>(game,0xa8)!=world||!result(game)){LOG("modern result unavailable");return;}
 const int winner=read<int>(world,0x1b8),mode=winner<0||winner>1?3:winner==owner?1:2;
 using Present=void(*)(void*,int,int,int,int,int,int,int,int,int,int,int);
 const int own=actual_crowns[owner],other=actual_crowns[1-owner];guard.unlock();
 at<Present>(0x9d4a14)(screen,mode,0,0,own,other,0,0,0,0,0,0);
}
void transition(void* manager){
 {
  std::lock_guard<std::mutex> guard(lock);
  // 原退出确认先从战斗(4)进入游戏重载(3)，在重载前交给宿主关闭。
  if(manager&&submitted&&read<int>(manager,0x38)==4&&read<int>(manager,0x3c)==3){
   if(!exit_requested.exchange(true,std::memory_order_acq_rel))LOG("replay exit requested current=4 next=3");
   return;
  }
 }
 original_transition(manager);
}
void lifecycle(void* manager,float delta){
 original_lifecycle(manager,delta);
 manager=at<void*(*)()>(0xed099c)();
 std::lock_guard<std::mutex> guard(lock);
 if(!manager)return;
 void* root=*reinterpret_cast<void**>(base+0x1d34ae0);if(!root)return;
 void* context=read<void*>(root,0x20);if(!context)return;
 auto schedule=at<void(*)(void*,int,int)>(0xed2560);
 if(!pending.empty()&&!loading){void* list=read<void*>(manager,0x30);if(!list)return;loading=true;schedule(manager,1,0);LOG("modern loading");}
 if(!pending.empty()&&!submitted&&read<uint8_t>(context,0xb00)&&read<int>(manager,0x38)==1){
  void* viewer=*reinterpret_cast<void**>(base+0x1d3c748);if(!viewer)return;write(viewer,0x20,identity[0]);write(viewer,0x24,identity[1]);
  void* string=at<void*(*)(size_t)>(0x1b34888)(16);if(!string)return;
  at<void(*)(void*,const char*,int)>(0x16cb4c8)(string,pending.c_str(),static_cast<int>(pending.size()));
  void* node=at<void*(*)(void*,void*,int)>(0x1537d58)(string,read<void*>(manager,0x88),100);
  at<void(*)(void*)>(0x16cb3c8)(string);at<void(*)(void*)>(0x1b34938)(string);
  if(!node){LOG("modern parse failed");pending.clear();return;}
  write(manager,0x88,node);write(manager,0x5c,-1);schedule(manager,4,0);write(manager,0x14,3);submitted_ns.store(monotonic_ns());submitted=true;pending.clear();LOG("modern submitted owner=%d",owner);
 }
 battle_rendering.store(submitted&&read<int>(manager,0x38)==4,std::memory_order_release);
 if(!submitted||finished.load(std::memory_order_acquire)||read<int>(manager,0x38)!=4)return;
 void* controller=read<void*>(manager,0x28);if(!controller)return;
 #ifdef CR_REPLAY_TESTING
 char speed[PROP_VALUE_MAX]={0};__system_property_get("debug.crhistory.proof.speed",speed);const int requested=std::atoi(speed);if(requested==1||requested==4||requested==16)write(controller,0x74,static_cast<float>(requested));
#endif
 void* game=read<void*>(controller,0x90);if(game){
  const int tick=read<int>(game,0x60);
  if(tick>=0&&(last_progress_tick<0||tick-last_progress_tick>=100)){last_progress_tick=tick;LOG("modern progress tick=%d",tick);}
  result(game);
 }
}
}
extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM*,void*){
 void* handle=dlopen("libg.so",RTLD_NOW);if(!handle)return JNI_ERR;
 void* symbol=dlsym(handle,"Java_com_supercell_titan_GameApp_createGameMain");Dl_info info{};
 if(!symbol||!dladdr(symbol,&info))return JNI_ERR;base=reinterpret_cast<uintptr_t>(info.dli_fbase);
 if(reinterpret_cast<uintptr_t>(symbol)-base!=0x16fbf10)return JNI_ERR;
 const uint8_t life[]={0xe9,0x23,0xbc,0x6d,0xfd,0x7b,0x1,0xa9,0xf5,0x13,0x0,0xf9,0xf4,0x4f,0x3,0xa9};
 const uint8_t change[]={0xfd,0x7b,0xbd,0xa9,0xf6,0x57,0x01,0xa9,0xf4,0x4f,0x02,0xa9,0xfd,0x03,0x00,0x91};
 const uint8_t view[]={0xff,0x83,0x01,0xd1,0xfd,0x7b,0x03,0xa9,0xf6,0x57,0x04,0xa9,0xf4,0x4f,0x05,0xa9};
 const uint8_t ev[]={0xff,0x3,0x3,0xd1,0xfd,0x7b,0x8,0xa9,0xf8,0x5f,0x9,0xa9,0xf6,0x57,0xa,0xa9};
 if(std::memcmp(reinterpret_cast<void*>(base+0x9d3c64),view,16)||std::memcmp(reinterpret_cast<void*>(base+0xed1f40),life,16)||std::memcmp(reinterpret_cast<void*>(base+0xed22b4),change,16)||std::memcmp(reinterpret_cast<void*>(base+0x888f74),ev,16))return JNI_ERR;
 installed=hook(0x9d3c64,view,reinterpret_cast<void*>(presentation),reinterpret_cast<void**>(&original_presentation))&&hook(0x888f74,ev,reinterpret_cast<void*>(event),reinterpret_cast<void**>(&original_event))&&hook(0xed1f40,life,reinterpret_cast<void*>(lifecycle),reinterpret_cast<void**>(&original_lifecycle));
 installed=installed&&hook(0xed22b4,change,reinterpret_cast<void*>(transition),reinterpret_cast<void**>(&original_transition));
 if(installed)dl_iterate_phdr(install_frame_probe,handle);
 LOG("modern bridge installed=%d",installed);return installed?JNI_VERSION_1_6:JNI_ERR;
}
extern "C" JNIEXPORT jboolean JNICALL Java_cn_crhistory_player_ReplayBootstrap_submit(JNIEnv* env,jclass,jbyteArray input,jstring,jlong hi,jlong lo){
 if(!installed||!input||hi<0||hi>UINT32_MAX||lo<0||lo>UINT32_MAX)return false;const auto n=env->GetArrayLength(input);if(n<=0||n>16*1024*1024)return false;
 std::lock_guard<std::mutex> guard(lock);if(submitted||!pending.empty())return false;
 pending.resize(n);env->GetByteArrayRegion(input,0,n,reinterpret_cast<jbyte*>(&pending[0]));if(env->ExceptionCheck()){pending.clear();return false;}identity[0]=static_cast<uint32_t>(hi);identity[1]=static_cast<uint32_t>(lo);return true;
}
extern "C" JNIEXPORT jboolean JNICALL Java_cn_crhistory_player_ReplayBootstrap_hasFinished(JNIEnv*,jclass){return finished.load(std::memory_order_acquire);}
extern "C" JNIEXPORT jboolean JNICALL Java_cn_crhistory_player_ReplayBootstrap_shouldExit(JNIEnv*,jclass){return exit_requested.load(std::memory_order_acquire);}
extern "C" JNIEXPORT jstring JNICALL Java_cn_crhistory_player_ReplayBootstrap_result(JNIEnv* env,jclass){std::lock_guard<std::mutex> guard(lock);return finished.load(std::memory_order_acquire)?env->NewStringUTF(completion.c_str()):nullptr;}

extern "C" JNIEXPORT jint JNICALL Java_cn_crhistory_player_ReplayBootstrap_presentedFrames(JNIEnv*,jclass){return frame_probe.load(std::memory_order_acquire)?presented_frames.load(std::memory_order_relaxed):-1;}

extern "C" JNIEXPORT jlong JNICALL Java_cn_crhistory_player_ReplayBootstrap_firstFrameElapsed(JNIEnv*,jclass){const int64_t first=first_frame_ns.load(),start=submitted_ns.load();return first>=start&&start>0?(first-start)/1000000:-1;}

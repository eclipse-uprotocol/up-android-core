// This is the interface to the JVM that we'll call the majority of our
// methods on.
use jni::JNIEnv;

// These objects are what you should use as arguments to your native
// function. They carry extra lifetime information to prevent them escaping
// this context and getting used after being GC'd.
use jni::objects::{GlobalRef, JClass, JString};

// This is just a pointer. We'll be returning it from our function. We
// can't return one of the objects with lifetime information because the
// lifetime checker won't let us.
use jni::sys::{jstring, jobject};

use binder_ndk_sys::AIBinder;
use binder::unstable_api::new_spibinder;
use binder::{FromIBinder, Strong};

use aidl_rust_codegen::binder_impls::IUBus::IUBus;
use aidl_rust_codegen::binder_impls::IUListener::IUListener;
use aidl_rust_codegen::parcelable_stubs::{ParcelableUEntity, ParcelableUStatus, ParcelableUUri, ParcelableUMessage};

struct UStreamerGlue {

}

// This keeps Rust from "mangling" the name and making it unique for this
// crate.
#[no_mangle]
pub extern "system" fn Java_org_eclipse_uprotocol_core_ustreamer_UStreamerGlue_forwardJavaBinder<'local>(mut env: JNIEnv<'local>,
// This is the class that owns our static method. It's not going to be used,
// but still must be present to match the expected signature of a static
// native method.
                                                     class: JClass<'local>,
                                                     binder: jobject)
                                                     -> jstring {

    // TODO: Here we'd do the dance of turning a Java Binder object into a strongly typed Rust binder interface
    let aibinder = unsafe { binder_ndk_sys::AIBinder_fromJavaBinder(env.get_raw(), binder) };
    let spibinder = unsafe { new_spibinder(aibinder) };

    let spibinder_success = if spibinder.is_none() {
        "failed to get SpIBinder"
    } else {
        "got SpIBinder"
    };

    let spibinder = spibinder.unwrap();

    let remote_service = if spibinder.is_remote() {
        "remote service"
    } else {
        "local service"
    };

    let ubus = spibinder.into_interface::<dyn IUBus>();

    let into_interface_success = if ubus.is_err() {
        format!("into_interface to ubus failed: {:?}", &ubus)
    } else {
        format!("into_interface to ubus succeeded: {:?}", &ubus)
    };

    let empty_string = "";
    let status_strings = vec![empty_string, spibinder_success, remote_service, &into_interface_success];
    let status_string = status_strings.join("\n");

    // Then we have to create a new Java string to return. Again, more info
    // in the `strings` module.
    let output = env.new_string(status_string)
        .expect("Couldn't create java string!");

    // Finally, extract the raw pointer to return.
    output.into_raw()
}
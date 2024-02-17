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
use binder::{BinderFeatures, FromIBinder, Interface, Strong};
use binder::binder_impl::Binder;

use aidl_rust_codegen::binder_impls::IUBus::IUBus;
use aidl_rust_codegen::binder_impls::IUListener::{IUListener, BnUListener};
use aidl_rust_codegen::parcelable_stubs::*;

use up_rust::uprotocol::{UAuthority, UEntity, UResource, UUri};
use protobuf::Message;

use once_cell::sync::OnceCell;

use std::any::type_name;
use std::time::Duration;
use async_std::sync::{Arc, Mutex};
use async_std::task;

fn type_of<T>(_: &T) -> &'static str {
    type_name::<T>()
}

pub struct MyIUListener;

impl Interface for MyIUListener {}

impl IUListener for MyIUListener {
    fn onReceive(&self, event: &ParcelableUMessage) -> binder::Result<()> {
        println!("received ParcelableUMessage: {:?}", event);
        Ok(())
    }
}

static INSTANCE: OnceCell<Arc<Strong<dyn IUBus>>> = OnceCell::new();

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

    let ubus = Arc::new(spibinder.into_interface::<dyn IUBus>().expect("Unable to obtain strong interface"));

    INSTANCE.set(ubus).unwrap_or_else(|_| panic!("Instance was already set!"));

    let type_of_ubus = type_of(&INSTANCE.get().expect("ubus is not initialized"));

    let package_name = "org.eclipse.uprotocol.core.ustreamer";
    let uentity = UEntity {
        name: "ustreamer_glue".to_string(),
        version_major: Some(1),
        ..Default::default()
    };

    let uentity_computed_size = format!("uentity computed size: {}", uentity.compute_size());

    let my_flags: i32 = 0;
    let client_token = Binder::new(()).as_binder();
    let my_iulistener = MyIUListener;
    let my_iulistener_binder = BnUListener::new_binder(my_iulistener, BinderFeatures::default());

    let bytes = uentity.write_to_bytes().unwrap();
    let size = bytes.len() as i32;

    let uentity_size = format!("uentity_size: {}", size);
    let uentity_bytes = format!("bytes: {:?}", bytes);

    let ustatus_registerClient = INSTANCE.get().expect("ubus is not initialized").registerClient(&package_name, &uentity.into(), &client_token, my_flags, &my_iulistener_binder);

    let ustatus_registerClient_string = format!("ustatus_registerClient: {:?}", ustatus_registerClient);

    let good_uuri = UUri {
        entity: Some(UEntity {
            name: "topic_to_subscribe_to".to_string(),
            ..Default::default()
        }).into(),
        resource: Some(UResource {
            name: "resource_i_want".to_string(),
            ..Default::default()
        }).into(),
        ..Default::default()
    };

    let bad_uuri = UUri {
        entity: Some(UEntity {
            name: "topic_to_subscribe_to".to_string(),
            ..Default::default()
        }).into(),
        ..Default::default()
    };

    let ustatus_enableDispatching_success = INSTANCE.get().expect("ubus is not initialized").enableDispatching(&good_uuri.clone().into(), my_flags, &client_token);
    let ustatus_enableDispatching_success_string = format!("ustatus_enableDispatching_success: {:?}", ustatus_enableDispatching_success);

    let ustatus_enableDispatching_failure = INSTANCE.get().expect("ubus is not initialized").enableDispatching(&bad_uuri.into(), my_flags, &client_token);
    let ustatus_enableDispatching_failure_string = format!("ustatus_enableDispatching_failure: {:?}", ustatus_enableDispatching_failure);

    let ustatus_disableDispatching_success = INSTANCE.get().expect("ubus is not initialized").disableDispatching(&good_uuri.clone().into(), my_flags, &client_token);
    let ustatus_disableDispatching_success_string = format!("ustatus_disableDispatching_success: {:?}", ustatus_disableDispatching_success);

    task::spawn(async move {
        loop {
            let ustatus_enableDispatchingTask_success = INSTANCE.get().expect("ubus is not initialized").enableDispatching(&good_uuri.clone().into(), my_flags, &client_token);
            let ustatus_enableDispatchingTask_success_string = format!("ustatus_enableDispatching_success: {:?}", ustatus_enableDispatching_success);

            let ustatus_disableDispatchingTask_success = INSTANCE.get().expect("ubus is not initialized").disableDispatching(&good_uuri.clone().into(), my_flags, &client_token);
            let ustatus_disableDispatchingTask_success_string = format!("ustatus_disableDispatching_success: {:?}", ustatus_disableDispatching_success);

            task::sleep(Duration::from_millis(100)).await;
        }
    });

    let empty_string = "";
    let status_strings = vec![empty_string,
                              spibinder_success,
                              remote_service,
                              type_of_ubus,
                              &uentity_computed_size,
                              &uentity_size,
                              &uentity_bytes,
                              &ustatus_registerClient_string,
                              &ustatus_enableDispatching_success_string,
                              &ustatus_enableDispatching_failure_string,
                              &ustatus_disableDispatching_success_string];
    let status_string = status_strings.join("\n");

    // Then we have to create a new Java string to return. Again, more info
    // in the `strings` module.
    let output = env.new_string(status_string)
        .expect("Couldn't create java string!");

    // Finally, extract the raw pointer to return.
    output.into_raw()
}
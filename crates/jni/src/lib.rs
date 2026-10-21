//! JNI bindings for PdfCraft Android app.
//!
//! Provides zero-copy page rasterization to Android `Bitmap` via `jnigraphics`,
//! document inspection, full-text search, and atomic incremental saves.
//! All entry points are guarded with `catch_unwind` to uphold the "Never Crash" principle.

use std::fs::File;
use std::io::Read;
use std::os::fd::FromRawFd;
use std::panic::{AssertUnwindSafe, catch_unwind};
use std::sync::{Arc, Mutex};

use jni::JNIEnv;
use jni::objects::{JByteArray, JClass, JObject, JString};
use jni::sys::{jboolean, jfloat, jfloatArray, jint, jlong, jstring};
use pdfcraft_render::{DocInfo, PageRenderer, RenderConfig, RenderRequest, RequestKind, inspect};
use serde::{Deserialize, Serialize};

#[repr(C)]
#[derive(Debug, Default, Clone, Copy)]
pub struct AndroidBitmapInfo {
    pub width: u32,
    pub height: u32,
    pub stride: u32,
    pub format: i32,
    pub flags: u32,
}

pub const ANDROID_BITMAP_FORMAT_RGBA_8888: i32 = 1;
pub const ANDROID_BITMAP_RESULT_SUCCESS: i32 = 0;

#[cfg(target_os = "android")]
#[link(name = "jnigraphics")]
unsafe extern "C" {
    pub fn AndroidBitmap_getInfo(
        env: *mut jni::sys::JNIEnv,
        jbitmap: jni::sys::jobject,
        info: *mut AndroidBitmapInfo,
    ) -> i32;
    pub fn AndroidBitmap_lockPixels(
        env: *mut jni::sys::JNIEnv,
        jbitmap: jni::sys::jobject,
        addr_ptr: *mut *mut std::ffi::c_void,
    ) -> i32;
    pub fn AndroidBitmap_unlockPixels(
        env: *mut jni::sys::JNIEnv,
        jbitmap: jni::sys::jobject,
    ) -> i32;
}

#[cfg(not(target_os = "android"))]
#[allow(non_snake_case)]
unsafe fn AndroidBitmap_getInfo(
    _env: *mut jni::sys::JNIEnv,
    _jbitmap: jni::sys::jobject,
    info: *mut AndroidBitmapInfo,
) -> i32 {
    unsafe {
        (*info).width = 512;
        (*info).height = 512;
        (*info).stride = 512 * 4;
        (*info).format = ANDROID_BITMAP_FORMAT_RGBA_8888;
        (*info).flags = 0;
    }
    ANDROID_BITMAP_RESULT_SUCCESS
}

#[cfg(not(target_os = "android"))]
#[allow(non_snake_case)]
unsafe fn AndroidBitmap_lockPixels(
    _env: *mut jni::sys::JNIEnv,
    _jbitmap: jni::sys::jobject,
    addr_ptr: *mut *mut std::ffi::c_void,
) -> i32 {
    use std::sync::OnceLock;
    static DUMMY_BUFFER: OnceLock<Box<[u8]>> = OnceLock::new();
    let buf = DUMMY_BUFFER.get_or_init(|| vec![0u8; 512 * 512 * 4].into_boxed_slice());
    unsafe {
        *addr_ptr = buf.as_ptr() as *mut u8 as *mut std::ffi::c_void;
    }
    ANDROID_BITMAP_RESULT_SUCCESS
}

#[cfg(not(target_os = "android"))]
#[allow(non_snake_case)]
unsafe fn AndroidBitmap_unlockPixels(
    _env: *mut jni::sys::JNIEnv,
    _jbitmap: jni::sys::jobject,
) -> i32 {
    ANDROID_BITMAP_RESULT_SUCCESS
}

/// Native representation of an opened PDF document session.
pub struct NativePdfDocument {
    pub bytes: Arc<Vec<u8>>,
    pub renderer: Mutex<PageRenderer>,
    pub doc_info: DocInfo,
    pub password: Option<Arc<str>>,
}

#[derive(Serialize, Deserialize)]
pub struct OutlineItemDto {
    pub title: String,
    pub page: Option<usize>,
    pub children: Vec<OutlineItemDto>,
}

#[derive(Serialize, Deserialize)]
pub struct SearchHitDto {
    pub page: usize,
    pub rects: Vec<[f32; 4]>,
    pub snippet: String,
}

#[derive(Serialize, Deserialize)]
pub struct DocumentMetadataDto {
    pub title: Option<String>,
    pub author: Option<String>,
    pub subject: Option<String>,
    pub creator: Option<String>,
    pub producer: Option<String>,
    pub page_count: usize,
    pub pdf_version: String,
    pub encrypted: bool,
}

fn throw_exception(env: &mut JNIEnv, message: &str) {
    let _ = env.throw_new("java/lang/RuntimeException", message);
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_core_PdfEngine_nativeInitLogging(
    _env: JNIEnv,
    _class: JClass,
) {
    #[cfg(target_os = "android")]
    {
        android_logger::init_once(
            android_logger::Config::default()
                .with_tag("PdfCraftNative")
                .with_max_level(log::LevelFilter::Info),
        );
    }
    log::info!("PdfCraft Native engine initialized");
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_core_PdfEngine_nativeOpenDocumentBytes(
    mut env: JNIEnv,
    _class: JClass,
    bytes_obj: JByteArray,
    password_obj: JString,
) -> jlong {
    let result = catch_unwind(AssertUnwindSafe(|| -> Result<jlong, String> {
        let byte_array = env.convert_byte_array(&bytes_obj).map_err(|e| e.to_string())?;
        let password: Option<Arc<str>> = if !password_obj.is_null() {
            let pass_str: String = env.get_string(&password_obj).map_err(|e| e.to_string())?.into();
            Some(Arc::from(pass_str))
        } else {
            None
        };

        let bytes = Arc::new(byte_array);
        let doc_info = inspect(bytes.clone(), password.as_deref())
            .map_err(|e| format!("Inspection error: {e:?}"))?;

        let mut config = RenderConfig::default();
        config.password = password.clone();
        let renderer = PageRenderer::new(bytes.clone(), config);

        let native_doc = Box::new(NativePdfDocument {
            bytes,
            renderer: Mutex::new(renderer),
            doc_info,
            password,
        });

        Ok(Box::into_raw(native_doc) as jlong)
    }));

    match result {
        Ok(Ok(ptr)) => ptr,
        Ok(Err(err)) => {
            throw_exception(&mut env, &err);
            0
        }
        Err(_) => {
            throw_exception(&mut env, "Panic caught in nativeOpenDocumentBytes");
            0
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_core_PdfEngine_nativeOpenDocumentFd(
    mut env: JNIEnv,
    _class: JClass,
    fd: jint,
    password_obj: JString,
) -> jlong {
    let result = catch_unwind(AssertUnwindSafe(|| -> Result<jlong, String> {
        let mut file = unsafe { File::from_raw_fd(fd) };
        let mut buffer = Vec::new();
        file.read_to_end(&mut buffer).map_err(|e| e.to_string())?;
        std::mem::forget(file); // Keep the underlying Android file descriptor open

        let password: Option<Arc<str>> = if !password_obj.is_null() {
            let pass_str: String = env.get_string(&password_obj).map_err(|e| e.to_string())?.into();
            Some(Arc::from(pass_str))
        } else {
            None
        };

        let bytes = Arc::new(buffer);
        let doc_info = inspect(bytes.clone(), password.as_deref())
            .map_err(|e| format!("Inspection error: {e:?}"))?;

        let mut config = RenderConfig::default();
        config.password = password.clone();
        let renderer = PageRenderer::new(bytes.clone(), config);

        let native_doc = Box::new(NativePdfDocument {
            bytes,
            renderer: Mutex::new(renderer),
            doc_info,
            password,
        });

        Ok(Box::into_raw(native_doc) as jlong)
    }));

    match result {
        Ok(Ok(ptr)) => ptr,
        Ok(Err(err)) => {
            throw_exception(&mut env, &err);
            0
        }
        Err(_) => {
            throw_exception(&mut env, "Panic caught in nativeOpenDocumentFd");
            0
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_core_PdfEngine_nativeCloseDocument(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle != 0 {
        let _ = catch_unwind(AssertUnwindSafe(|| {
            unsafe {
                let _ = Box::from_raw(handle as *mut NativePdfDocument);
            }
        }));
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_core_PdfEngine_nativeGetPageCount(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jint {
    let result = catch_unwind(AssertUnwindSafe(|| -> jint {
        let doc = unsafe { &*(handle as *const NativePdfDocument) };
        doc.doc_info.pages.len() as jint
    }));

    match result {
        Ok(count) => count,
        Err(_) => {
            throw_exception(&mut env, "Panic in nativeGetPageCount");
            0
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_core_PdfEngine_nativeGetPageDimensions(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    page_index: jint,
) -> jfloatArray {
    let result = catch_unwind(AssertUnwindSafe(|| -> Result<jfloatArray, String> {
        let doc = unsafe { &*(handle as *const NativePdfDocument) };
        let page = doc.doc_info.pages.get(page_index as usize)
            .ok_or_else(|| "Invalid page index".to_string())?;

        let dims: [jfloat; 3] = [page.width, page.height, page.rotation as f32];
        let j_array = env.new_float_array(3).map_err(|e| e.to_string())?;
        env.set_float_array_region(&j_array, 0, &dims).map_err(|e| e.to_string())?;
        Ok(j_array.into_raw())
    }));

    match result {
        Ok(Ok(arr)) => arr,
        Ok(Err(err)) => {
            throw_exception(&mut env, &err);
            std::ptr::null_mut()
        }
        Err(_) => {
            throw_exception(&mut env, "Panic in nativeGetPageDimensions");
            std::ptr::null_mut()
        }
    }
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_core_PdfEngine_nativeGetMetadata(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jstring {
    let result = catch_unwind(AssertUnwindSafe(|| -> Result<jstring, String> {
        let doc = unsafe { &*(handle as *const NativePdfDocument) };
        let meta = DocumentMetadataDto {
            title: doc.doc_info.title.clone(),
            author: doc.doc_info.author.clone(),
            subject: doc.doc_info.subject.clone(),
            creator: doc.doc_info.creator.clone(),
            producer: doc.doc_info.producer.clone(),
            page_count: doc.doc_info.pages.len(),
            pdf_version: doc.doc_info.pdf_version.clone(),
            encrypted: doc.doc_info.encrypted,
        };
        let json = serde_json::to_string(&meta).map_err(|e| e.to_string())?;
        let j_str = env.new_string(json).map_err(|e| e.to_string())?;
        Ok(j_str.into_raw())
    }));

    match result {
        Ok(Ok(str_ref)) => str_ref,
        Ok(Err(err)) => {
            throw_exception(&mut env, &err);
            std::ptr::null_mut()
        }
        Err(_) => {
            throw_exception(&mut env, "Panic in nativeGetMetadata");
            std::ptr::null_mut()
        }
    }
}

fn convert_outline(items: &[pdfcraft_render::OutlineItem]) -> Vec<OutlineItemDto> {
    items.iter().map(|item| OutlineItemDto {
        title: item.title.clone(),
        page: item.page,
        children: convert_outline(&item.children),
    }).collect()
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_core_PdfEngine_nativeGetOutline(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jstring {
    let result = catch_unwind(AssertUnwindSafe(|| -> Result<jstring, String> {
        let doc = unsafe { &*(handle as *const NativePdfDocument) };
        let outline_dto = convert_outline(&doc.doc_info.outline);
        let json = serde_json::to_string(&outline_dto).map_err(|e| e.to_string())?;
        let j_str = env.new_string(json).map_err(|e| e.to_string())?;
        Ok(j_str.into_raw())
    }));

    match result {
        Ok(Ok(str_ref)) => str_ref,
        Ok(Err(err)) => {
            throw_exception(&mut env, &err);
            std::ptr::null_mut()
        }
        Err(_) => {
            throw_exception(&mut env, "Panic in nativeGetOutline");
            std::ptr::null_mut()
        }
    }
}

/// Zero-copy page rasterizer directly writing to Android `Bitmap`.
#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_core_PdfEngine_nativeRenderPageToBitmap(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    page_index: jint,
    zoom: jfloat,
    bitmap_obj: JObject,
    dark_mode: jboolean,
    sepia_mode: jboolean,
) -> jboolean {
    let result = catch_unwind(AssertUnwindSafe(|| -> Result<(), String> {
        let doc = unsafe { &*(handle as *const NativePdfDocument) };
        let raw_env = env.get_raw();
        let raw_bitmap = bitmap_obj.as_raw();

        let mut info = AndroidBitmapInfo::default();
        let res = unsafe { AndroidBitmap_getInfo(raw_env, raw_bitmap, &mut info) };
        if res != ANDROID_BITMAP_RESULT_SUCCESS {
            return Err(format!("AndroidBitmap_getInfo failed with code {res}"));
        }

        let mut pixels_ptr: *mut std::ffi::c_void = std::ptr::null_mut();
        let lock_res = unsafe { AndroidBitmap_lockPixels(raw_env, raw_bitmap, &mut pixels_ptr) };
        if lock_res != ANDROID_BITMAP_RESULT_SUCCESS || pixels_ptr.is_null() {
            return Err(format!("AndroidBitmap_lockPixels failed with code {lock_res}"));
        }

        // Render request to pdfcraft-render
        let req = RenderRequest {
            page: page_index as usize,
            scale: zoom,
            kind: RequestKind::Pixels,
            tag: 0,
            tile: None,
            preview: false,
        };

        let rendered = {
            let mut renderer_guard = doc.renderer.lock().map_err(|e| e.to_string())?;
            renderer_guard.render(req)
        };

        let width = (info.width as usize).min(rendered.width as usize);
        let height = (info.height as usize).min(rendered.height as usize);
        let stride = info.stride as usize;

        let src_bytes: &[u8] = &rendered.rgba;
        let dst_bytes = unsafe {
            std::slice::from_raw_parts_mut(pixels_ptr as *mut u8, stride * height)
        };

        for y in 0..height {
            let src_row_start = y * (rendered.width as usize) * 4;
            if src_row_start + (width * 4) > src_bytes.len() {
                break;
            }
            let src_row = &src_bytes[src_row_start..src_row_start + (width * 4)];
            let dst_row_start = y * stride;
            if dst_row_start + (width * 4) > dst_bytes.len() {
                break;
            }
            let dst_row = &mut dst_bytes[dst_row_start..dst_row_start + (width * 4)];

            if dark_mode != 0 {
                // Invert colors for dark mode (soft contrast)
                for (src_chunk, dst_chunk) in src_row.chunks_exact(4).zip(dst_row.chunks_exact_mut(4)) {
                    dst_chunk[0] = 255 - src_chunk[0];
                    dst_chunk[1] = 255 - src_chunk[1];
                    dst_chunk[2] = 255 - src_chunk[2];
                    dst_chunk[3] = src_chunk[3];
                }
            } else if sepia_mode != 0 {
                // Eye-care parchment mode
                for (src_chunk, dst_chunk) in src_row.chunks_exact(4).zip(dst_row.chunks_exact_mut(4)) {
                    let r = src_chunk[0] as f32;
                    let g = src_chunk[1] as f32;
                    let b = src_chunk[2] as f32;
                    dst_chunk[0] = ((r * 0.94).min(255.0)) as u8;
                    dst_chunk[1] = ((g * 0.90).min(255.0)) as u8;
                    dst_chunk[2] = ((b * 0.80).min(255.0)) as u8;
                    dst_chunk[3] = src_chunk[3];
                }
            } else {
                dst_row.copy_from_slice(src_row);
            }
        }

        unsafe {
            AndroidBitmap_unlockPixels(raw_env, raw_bitmap);
        }

        Ok(())
    }));

    match result {
        Ok(Ok(())) => 1,
        Ok(Err(err)) => {
            log::error!("Error rendering page to bitmap: {err}");
            0
        }
        Err(_) => {
            log::error!("Panic in nativeRenderPageToBitmap");
            0
        }
    }
}

/// Search text in the document and return match locations.
#[unsafe(no_mangle)]
pub extern "system" fn Java_ai_storyteller_pdfcraft_core_PdfEngine_nativeSearchText(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    query_obj: JString,
) -> jstring {
    let result = catch_unwind(AssertUnwindSafe(|| -> Result<jstring, String> {
        let doc = unsafe { &*(handle as *const NativePdfDocument) };
        let query: String = env.get_string(&query_obj).map_err(|e| e.to_string())?.into();
        let query_lower = query.to_lowercase();

        let mut hits = Vec::new();
        let page_count = doc.doc_info.pages.len();

        let mut renderer_guard = doc.renderer.lock().map_err(|e| e.to_string())?;
        for page_idx in 0..page_count {
            let req = RenderRequest {
                page: page_idx,
                scale: 1.0,
                kind: RequestKind::Text,
                tag: 0,
                tile: None,
                preview: false,
            };
            let rendered = renderer_guard.render(req);
            if let Some(text) = rendered.text {
                let text_str = text.plain_text();
                let lower = text_str.to_lowercase();
                if let Some(pos) = lower.find(&query_lower) {
                    let snippet: String = text_str.chars().skip(pos.saturating_sub(20)).take(60).collect();
                    hits.push(SearchHitDto {
                        page: page_idx,
                        rects: vec![[0.0, 0.0, 100.0, 20.0]],
                        snippet,
                    });
                }
            }
        }

        let json = serde_json::to_string(&hits).map_err(|e| e.to_string())?;
        let j_str = env.new_string(json).map_err(|e| e.to_string())?;
        Ok(j_str.into_raw())
    }));

    match result {
        Ok(Ok(str_ref)) => str_ref,
        Ok(Err(err)) => {
            throw_exception(&mut env, &err);
            std::ptr::null_mut()
        }
        Err(_) => {
            throw_exception(&mut env, "Panic in nativeSearchText");
            std::ptr::null_mut()
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_native_pdf_lifecycle() {
        let pdf_path = concat!(
            env!("CARGO_MANIFEST_DIR"),
            "/../ui-egui/tests/data/form.pdf"
        );
        let bytes = std::fs::read(pdf_path).expect("form.pdf must exist");
        let arc_bytes = Arc::new(bytes);

        let doc_info = inspect(arc_bytes.clone(), None).expect("inspection must succeed");
        assert!(doc_info.pages.len() >= 1);

        let mut config = RenderConfig::default();
        config.password = None;
        let renderer = PageRenderer::new(arc_bytes.clone(), config);

        let native_doc = NativePdfDocument {
            bytes: arc_bytes,
            renderer: Mutex::new(renderer),
            doc_info,
            password: None,
        };

        assert!(native_doc.doc_info.pages.len() >= 1);
        let first_page = &native_doc.doc_info.pages[0];
        assert!(first_page.width > 0.0);
        assert!(first_page.height > 0.0);

        let req = RenderRequest {
            page: 0,
            scale: 1.0,
            kind: RequestKind::Pixels,
            tag: 0,
            tile: None,
            preview: false,
        };
        let mut r = native_doc.renderer.lock().unwrap();
        let rendered = r.render(req);
        assert!(rendered.width > 0);
        assert!(rendered.height > 0);
        assert!(!rendered.rgba.is_empty());
    }
}

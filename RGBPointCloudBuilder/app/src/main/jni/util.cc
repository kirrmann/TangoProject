/*
 * Copyright 2014 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <tango-gl/conversions.h>
#include "rgb-depth-sync/util.h"

namespace rgb_depth_sync {

glm::mat4 util::GetMatrixFromPose(const TangoPoseData* pose_data) {
  glm::vec3 translation =
      glm::vec3(pose_data->translation[0], pose_data->translation[1],
                pose_data->translation[2]);
  glm::quat rotation =
      glm::quat(pose_data->orientation[3], pose_data->orientation[0],
                pose_data->orientation[1], pose_data->orientation[2]);
  return glm::translate(glm::mat4(1.0f), translation) *
         glm::mat4_cast(rotation);
}

glm::quat util::GetRotationFromMatrix(glm::mat4 transformation) {
  glm::vec3 translation;
  glm::quat rotation;
  glm::vec3 scale;

  tango_gl::util::DecomposeMatrix(transformation, translation, rotation, scale);

  return  rotation;
}

glm::vec3 util::GetTranslationFromMatrix(glm::mat4 transformation) {
  glm::vec3 translation;
  glm::quat rotation;
  glm::vec3 scale;

  tango_gl::util::DecomposeMatrix(transformation, translation, rotation, scale);

  return translation;
}

glm::mat4 util::GetPoseAppliedOpenGLWorldFrame( const glm::mat4 pose_matrix) {
  // This full multiplication is equal to:
  //   opengl_world_T_opengl_camera =
  //      opengl_world_T_start_service *
  //      start_service_T_device *
  //      device_T_imu *
  //      imu_T_color_camera *
  //      depth_camera_T_opengl_camera;
  //
  // More information about frame transformation can be found here:
  // Frame of reference:
  //   https://developers.google.com/project-tango/overview/frames-of-reference
  // Coordinate System Conventions:
  //   https://developers.google.com/project-tango/overview/coordinate-systems
  return tango_gl::conversions::opengl_world_T_tango_world()
         * pose_matrix;
}

}  // namespace rgb_depth_sync

package com.main10.domain.place.service;

import com.main10.domain.bookmark.entity.Bookmark;
import com.main10.domain.bookmark.repository.BookmarkRepository;
import com.main10.domain.place.dto.*;
import com.main10.domain.place.entity.PlaceCategory;
import com.main10.global.exception.BusinessLogicException;
import com.main10.global.exception.ExceptionCode;
import com.main10.global.file.FileHandler;
import com.main10.global.file.S3Upload;
import com.main10.global.file.UploadFile;
import com.main10.domain.member.entity.Member;
import com.main10.domain.member.service.MemberDbService;
import com.main10.domain.place.entity.Place;
import com.main10.domain.place.entity.PlaceImage;
import com.main10.domain.reserve.dto.ReserveDto;
import com.main10.domain.reserve.entity.Reserve;
import com.main10.global.batch.service.MbtiCountService;
import com.main10.domain.reserve.service.ReserveDbService;
import com.main10.domain.review.service.ReviewDbService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;
import javax.transaction.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.main10.domain.reserve.entity.Reserve.ReserveStatus.RESERVATION_CANCELED;


@Service
@RequiredArgsConstructor
public class PlaceService {

    private final PlaceDbService placeDbService;
    private final ReserveDbService reserveDbService;
    private final MemberDbService memberDbService;
    private final ReviewDbService reviewDbService;

    private final FileHandler fileHandler;
    private final MbtiCountService mbtiCountService;
    private final PlaceImageService placeImageService;
    private final PlaceCategoryService placeCategoryService;
    private final BookmarkRepository bookmarkRepository;

    @Autowired
    private S3Upload s3Upload;

    /**
     * ?????? ?????? (S3)
     *
     * @param placePostDto ?????? ?????? dto
     * @param memberId ????????? ?????????
     * @param files ????????? ??????
     * @author LimJaeminZ
     */
    @Transactional
    public void createPlaceS3(PlaceDto.Create placePostDto, Long memberId, List<MultipartFile> files) {

        Place place = Place.builder()
                .title(placePostDto.getTitle())
                .detailInfo(placePostDto.getDetailInfo())
                .maxCapacity(placePostDto.getMaxCapacity())
                .address(placePostDto.getAddress())
                .charge(placePostDto.getCharge())
                .memberId(memberId)
                .score(placePostDto.getScore())
                .endTime(placePostDto.getEndTime())
                .build();

        String dir = "placeImage";
        List<UploadFile> uploadFileList = s3Upload.uploadFileList(files, dir);
        List<PlaceImage> placeImageList = new ArrayList<>();

        uploadFileList.forEach(uploadFile -> {
            PlaceImage placeImage = PlaceImage.builder()
                    .originFileName(uploadFile.getOriginFileName())
                    .fileName(uploadFile.getFileName())
                    .filePath(uploadFile.getFilePath())
                    .fileSize(uploadFile.getFileSize())
                    .build();
            placeImageList.add(placeImage);
        });

        //????????? ????????? ??? ??????
        if (!placeImageList.isEmpty()) {
            for (PlaceImage placeImage : placeImageList) {
                //?????? DB ??????
                place.addPlaceImage(placeImage);
            }
        }
        placeDbService.savePlace(place);

        List<String> categoryList = placePostDto.getCategoryList().stream().distinct().collect(Collectors.toList());
        placeCategoryService.saveCategoryList(categoryList, place);
    }

    /**
     * ?????? ?????? (Local)
     *
     * @param placePostDto ?????? ?????? dto
     * @param memberId ????????? ?????????
     * @param files ????????? ??????
     * @throws Exception ??????
     * @author LimJaeminZ
     */
    @Transactional
    public void createPlace(PlaceDto.Create placePostDto, Long memberId, List<MultipartFile> files) throws Exception {

        Place place = Place.builder()
                .title(placePostDto.getTitle())
                .detailInfo(placePostDto.getDetailInfo())
                .maxCapacity(placePostDto.getMaxCapacity())
                .address(placePostDto.getAddress())
                .charge(placePostDto.getCharge())
                .memberId(memberId)
                .score(placePostDto.getScore())
                .endTime(placePostDto.getEndTime())
                .build();

        List<UploadFile> uploadFileList = fileHandler.parseUploadFileInfo(files);
        List<PlaceImage> placeImageList = new ArrayList<>();

        uploadFileList.forEach(uploadFile -> {
            PlaceImage placeImage = PlaceImage.builder()
                    .originFileName(uploadFile.getOriginFileName())
                    .fileName(uploadFile.getFileName())
                    .filePath(uploadFile.getFilePath())
                    .fileSize(uploadFile.getFileSize())
                    .build();
            placeImageList.add(placeImage);
        });

        //????????? ????????? ??? ??????
        if (!placeImageList.isEmpty()) {
            for (PlaceImage placeImage : placeImageList) {
                //?????? DB ??????
                place.addPlaceImage(placeImage);
            }
        }
        placeDbService.savePlace(place);

        List<String> categoryList = placePostDto.getCategoryList();
        placeCategoryService.saveCategoryList(categoryList, place);
    }

    /**
     * ?????? ?????? ?????? ?????????
     *
     * @param placeId ?????? ?????????
     * @param memberId ????????? ?????????
     * @return PlaceResponseDto
     * @author LimJaeminZ
     */
    @Transactional
    public PlaceDto.DetailResponse searchPlace(Long placeId, Long memberId) {//, List<String> filePath, List<String> categoryList) {
        boolean isBookmark = false;
        if (memberId != null) {
            Optional<Bookmark> findBookmark = bookmarkRepository
                    .findBookmarkByMemberIdAndPlaceId(memberId, placeId);
            if (findBookmark.isPresent())
                isBookmark = true;
        }

        Place place = placeDbService.ifExistsReturnPlace(placeId);
        Member findMember = memberDbService.ifExistsReturnMember(place.getMemberId());


        List<PlaceImageDto.Response> placeImageResponseDtoList = placeImageService.findAllByPlaceImagePath(placeId);
        List<String> categoryList = placeCategoryService.findByAllPlaceCategoryList(placeId);
        List<String> filePath = new ArrayList<>();
        List<ReserveDto.Detail> reserves = reserveDbService.findAllReserveForPlace(placeId);

        for (PlaceImageDto.Response placeImageResponseDto : placeImageResponseDtoList)
            filePath.add(placeImageResponseDto.getFilePath());

        return new PlaceDto.DetailResponse(place, filePath, categoryList, findMember, isBookmark, reserves);
    }

    /**
     * ?????? ?????? (S3)
     *
     * @param placeId ?????? ?????????
     * @param placePatchDto ?????? ?????? dto
     * @param memberId ????????? ?????????
     * @param files ????????? ??????
     * @author LimjaeminZ
     */
    public void updatePlaceS3(Long placeId, PlaceDto.Update placePatchDto, Long memberId, List<MultipartFile> files) {

        Place updatePlace = placeDbService.ifExistsReturnPlace(placeId);

        if (!Objects.equals(updatePlace.getMemberId(), memberId)) {
            throw new BusinessLogicException(ExceptionCode.UNAUTHORIZED_FOR_UPDATE);
        }

        updatePlace.setTitle(placePatchDto.getTitle());
        updatePlace.setDetailInfo(placePatchDto.getDetailInfo());
        updatePlace.setAddress(placePatchDto.getAddress());
        updatePlace.setCharge(placePatchDto.getCharge());
        updatePlace.setMaxCapacity(placePatchDto.getMaxCapacity());
        updatePlace.setEndTime(placePatchDto.getEndTime());

        List<PlaceCategory> dbCategoryList = placeCategoryService.findByAllPlaceCategoryList2(placeId); // db ?????? ???????????? ??????
        List<String> categoryList = placePatchDto.getCategoryList(); // ??????????????? ???????????? ??????
        List<String> addCategoryList; // ????????? ???????????? ??? ??????????????? ????????? List

        // ?????? ???????????? ??????
        addCategoryList = placeCategoryService.getAddCategoryList(updatePlace.getId(), dbCategoryList, categoryList);
        addCategoryList = addCategoryList.stream().distinct().collect(Collectors.toList());

        List<PlaceImage> dbPlaceImageList = placeImageService.findAllByPlaceImage(placeId); // db ?????? ?????? ??????
        List<MultipartFile> addFileList; // ????????? ??????????????? ???????????? ????????? ????????? List

        // ?????? ????????? ??????
        String dir = "placeImage";
        addFileList = getAddFileList(dbPlaceImageList, files, dir);

        List<UploadFile> uploadFileList = s3Upload.uploadFileList(addFileList, dir);
        List<PlaceImage> placeImageList = new ArrayList<>();

        uploadFileList.forEach(uploadFile -> {
            PlaceImage placeImage = PlaceImage.builder()
                    .originFileName(uploadFile.getOriginFileName())
                    .fileName(uploadFile.getFileName())
                    .filePath(uploadFile.getFilePath())
                    .fileSize(uploadFile.getFileSize())
                    .build();
            placeImageList.add(placeImage);
        });

        //????????? ????????? ??? ??????
        if (!placeImageList.isEmpty()) {
            for (PlaceImage placeImage : placeImageList) {
                //?????? DB ??????
                updatePlace.addPlaceImage(placeDbService.savePlaceImage(placeImage));
            }
        }
        placeDbService.savePlace(updatePlace);
        placeCategoryService.saveCategoryList(addCategoryList, updatePlace);
    }


    /**
     * ?????? ?????? (Local)
     *
     * @param placeId ?????? ?????????
     * @param placePatchDto ?????? ?????? dto
     * @param memberId ????????? ?????????
     * @param files ????????? ??????
     * @throws Exception ??????
     * @author LimjaeminZ
     */
    public void updatePlace(Long placeId, PlaceDto.Update placePatchDto, Long memberId, List<MultipartFile> files) throws Exception {

        Place updatePlace = placeDbService.ifExistsReturnPlace(placeId);

        if (!Objects.equals(updatePlace.getMemberId(), memberId)) {
            throw new BusinessLogicException(ExceptionCode.UNAUTHORIZED_FOR_UPDATE);
        }
        updatePlace.setTitle(placePatchDto.getTitle());
        updatePlace.setDetailInfo(placePatchDto.getDetailInfo());
        updatePlace.setAddress(placePatchDto.getAddress());
        updatePlace.setCharge(placePatchDto.getCharge());
        updatePlace.setMaxCapacity(placePatchDto.getMaxCapacity());
        updatePlace.setEndTime(placePatchDto.getEndTime());

        List<PlaceCategory> dbCategoryList = placeCategoryService.findByAllPlaceCategoryList2(placeId); // db ?????? ???????????? ??????
        List<String> categoryList = placePatchDto.getCategoryList(); // ??????????????? ???????????? ??????
        List<String> addCategoryList; // ????????? ???????????? ??? ??????????????? ????????? List

        // ?????? ???????????? ??????
        addCategoryList = placeCategoryService.getAddCategoryList(updatePlace.getId(), dbCategoryList, categoryList);

        List<PlaceImage> dbPlaceImageList = placeImageService.findAllByPlaceImage(placeId); // db ?????? ?????? ??????
        List<MultipartFile> addFileList; // ????????? ??????????????? ???????????? ????????? ????????? List

        // ?????? ????????? ??????
        String dir = "Local";
        addFileList = getAddFileList(dbPlaceImageList, files, dir);

        List<UploadFile> uploadFileList = fileHandler.parseUploadFileInfo(addFileList);
        List<PlaceImage> placeImageList = new ArrayList<>();

        uploadFileList.forEach(uploadFile -> {
            PlaceImage placeImage = PlaceImage.builder()
                    .originFileName(uploadFile.getOriginFileName())
                    .fileName(uploadFile.getFileName())
                    .filePath(uploadFile.getFilePath())
                    .fileSize(uploadFile.getFileSize())
                    .build();
            placeImageList.add(placeImage);
        });

        //????????? ????????? ??? ??????
        if (!placeImageList.isEmpty()) {
            for (PlaceImage placeImage : placeImageList) {
                //?????? DB ??????
                updatePlace.addPlaceImage(placeImage);
            }
        }
        placeDbService.savePlace(updatePlace);
        placeCategoryService.saveCategoryList(addCategoryList, updatePlace);

        // ?????? ?????? ?????? maxSpace update
        // timeStatusService.updateIsFull(updatePlace);
    }

    /**
     * ?????? ?????? ?????????
     *
     * @param memberId ????????? ?????????
     * @param placeId ?????? ?????????
     * @author Quartz614
     */
    @Transactional
    public void deleteHosting(Long memberId, Long placeId) {
        Member findMember = memberDbService.ifExistsReturnMember(memberId);
        Place findPlace = placeDbService.ifExistsReturnPlace(placeId);
        List<Reserve> findReserve = reserveDbService.findAllByReserves(placeId);

        // ?????? ??????
        reviewDbService.deleteAllByReviews(placeId);

        // ????????? ??????
        bookmarkRepository.deleteAllByPlaceId(placeId);

        // ?????? ?????? ??? ?????? ??????
        for (Reserve reserve : findReserve) {
            if (reserve.getStatus().equals(RESERVATION_CANCELED)) {
                throw new BusinessLogicException(ExceptionCode.RESERVATION_NOT_FOUND);
            }

            // mbtiCount -1
            mbtiCountService.reduceMbtiCount(findMember, placeId);

            // ?????? ?????? ?????? ... -> RESERVATION_CANCELED
            reserve.setStatus(RESERVATION_CANCELED);
            reserveDbService.saveReserve(reserve);
        }

        // ?????? ???????????? & ?????? ??????
        placeDbService.deletePlaceCategory(placeId);
        placeDbService.deletePlace(findPlace);
    }

    /**
     * ????????? ???????????? ?????? ????????? ?????? ?????????
     *
     * @param dbPlaceImageList ????????? ????????? ?????????
     * @param multipartFileList ????????? ????????? ?????????
     * @param dir Local/S3
     * @return List<MultipartFile>
     * @author LimJaeminZ
     */
    private List<MultipartFile> getAddFileList(List<PlaceImage> dbPlaceImageList, List<MultipartFile> multipartFileList, String dir) {
        List<MultipartFile> addFileList = new ArrayList<>(); // ????????? ??????????????? ???????????? ????????? ????????? List

        if (CollectionUtils.isEmpty(dbPlaceImageList)) { // db ?????? x
            if (!CollectionUtils.isEmpty(multipartFileList)) { // ????????? ?????? ?????? ??????
                // ?????? ?????? ????????? ??????
                addFileList.addAll(multipartFileList);
            }
        } else { // db??? ?????? ?????? ??????
            if (CollectionUtils.isEmpty(multipartFileList)) { // ????????? ?????? x
                dbPlaceImageList.forEach(
                        placeImage -> {
                            if(dir.equals("Local")) {
                                placeImageService.delete(placeImage.getId());
                            }
                            else {
                                placeImageService.delete(placeImage.getId());
                                s3Upload.delete(placeImage.getFileName(), dir);
                            }
                        }
                );
            } else { // ????????? ?????? ?????? ??????
                List<String> dbOriginFileNameList = new ArrayList<>();
                List<String> multipartFileOriginNameList = new ArrayList<>();
                multipartFileList.forEach(
                        multipartFile -> multipartFileOriginNameList.add(multipartFile.getOriginalFilename())
                );
                for (PlaceImage placeImage : dbPlaceImageList) {

                    String dbOriginFileName = placeImage.getOriginFileName();

                    if (!multipartFileOriginNameList.contains(dbOriginFileName)) {
                        if(dir.equals("Local")) {
                            placeImageService.delete(placeImage.getId());
                        }
                        else {
                            placeImageService.delete(placeImage.getId());
                            s3Upload.delete(placeImage.getFileName(), dir);
                        }
                    } else {
                        dbOriginFileNameList.add(dbOriginFileName);
                    }
                }
                for (MultipartFile multipartFile : multipartFileList) {
                    String multipartOriginName = multipartFile.getOriginalFilename();
                    if (!dbOriginFileNameList.contains(multipartOriginName)) {
                        addFileList.add(multipartFile);
                    }
                }
            }
        }
        return addFileList;
    }
}




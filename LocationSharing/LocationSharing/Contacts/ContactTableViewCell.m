/* Copyright (c) 2017 BlackBerry.  All Rights Reserved.
* 
* Licensed under the Apache License, Version 2.0 (the "License"); 
* you may not use this file except in compliance with the License. 
* You may obtain a copy of the License at 
* 
* http://www.apache.org/licenses/LICENSE-2.0 
* 
* Unless required by applicable law or agreed to in writing, software 
* distributed under the License is distributed on an "AS IS" BASIS, 
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
* See the License for the specific language governing permissions and 
* limitations under the License. 
  
* This sample code was created by BlackBerry using SDKs from Apple Inc. 
* and may contain code licensed for use only with Apple products. 
* Please review your Apple SDK Agreement for additional details. 
*/ 

#import "ContactTableViewCell.h"

@interface ContactTableViewCell ()
@property (weak, nonatomic) IBOutlet UIImageView *avatarImageView;
@property (weak, nonatomic) IBOutlet UILabel *nameLabel;
@property (weak, nonatomic) IBOutlet UILabel *emailLabel;
@end

@implementation ContactTableViewCell

- (void)setContact:(BBMAppUser *)contact
{
    self.nameLabel.text = contact.name;
    self.emailLabel.text = contact.email;
    [self loadImageUrl:contact.avatarUrl];
}

- (void)loadImageUrl:(NSString *)url
{
    //If tag changes by the time we have the data to load the image we do not display it because it
    //means the cell has been reused.
    self.avatarImageView.tag++;
    NSInteger oldTag = self.avatarImageView.tag;
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
        NSData *data = [NSData dataWithContentsOfURL:[NSURL URLWithString:url]];
        UIImage *image = [UIImage imageWithData:data];
        dispatch_async(dispatch_get_main_queue(), ^{
            if(self.avatarImageView.tag == oldTag) {
                self.avatarImageView.image = image;
            }
        });
    });

}

@end
